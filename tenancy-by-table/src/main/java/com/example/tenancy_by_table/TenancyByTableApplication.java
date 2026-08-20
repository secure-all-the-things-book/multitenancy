package com.example.tenancy_by_table;

import io.arconia.multitenancy.core.context.TenantContext;
import io.arconia.multitenancy.core.tenantdetails.TenantDetails;
import io.arconia.multitenancy.core.tenantdetails.TenantDetailsService;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.data.jdbc.core.convert.DelegatingDataAccessStrategy;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.QueryMappingConfiguration;
import org.springframework.data.jdbc.core.dialect.JdbcDialect;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.data.jdbc.repository.config.JdbcConfiguration;
import org.springframework.data.jdbc.repository.support.JdbcRepositoryFactoryBean;
import org.springframework.data.relational.RelationalManagedTypes;
import org.springframework.data.relational.core.mapping.NamingStrategy;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@EnableJdbcRepositories(repositoryFactoryBeanClass = TenantRoutingJdbcRepositoryFactoryBean.class)
public class TenancyByTableApplication {

	public static void main(String[] args) {
		SpringApplication.run(TenancyByTableApplication.class, args);
	}

}

class InitializingTenantDetailsService implements TenantDetailsService {

	private final Map<String, AtomicBoolean> tenantInitialized = new ConcurrentHashMap<>();

	private final TenantDetailsService delegate;

	private final ObjectProvider<DataSource> dataSourceProvider;

	InitializingTenantDetailsService(TenantDetailsService delegate, ObjectProvider<DataSource> dataSourceProvider) {
		this.delegate = delegate;
		this.dataSourceProvider = dataSourceProvider;
	}

	@Override
	public List<? extends TenantDetails> loadAllTenants() {
		return this.delegate.loadAllTenants();
	}

	@Override
	public @Nullable TenantDetails loadTenantByIdentifier(String identifier) {
		this.initialize(identifier);
		return this.delegate.loadTenantByIdentifier(identifier);
	}

	private void initialize(String identifier) {
		if (this.tenantInitialized.computeIfAbsent(identifier, _ -> new AtomicBoolean()).compareAndSet(false, true)) {
			IO.println("initializing tenant " + identifier + '.');
			Flyway.configure(getClass().getClassLoader())
				// every tenant shares the one schema, so from the second tenant onwards
				// the
				// schema is never empty and Flyway insists on a baseline. Baselining at 0
				// (rather than the default 1) is what keeps this tenant's own V1 in scope
				// --
				// otherwise Flyway marks it as already applied and creates no tables at
				// all.
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.table(identifier + "_flyway_schema_history")
				.dataSource(this.dataSourceProvider.getIfAvailable())
				.placeholders(Map.of("table_prefix", identifier))
				.locations("classpath:db/common", "classpath:db/tenants/" + identifier)
				.load()
				.migrate();
		}
	}

}

@Component
class TenantContextDetailsBeanPostProcessor implements BeanPostProcessor {

	private final ObjectProvider<DataSource> dataSourceObjectProvider;

	TenantContextDetailsBeanPostProcessor(ObjectProvider<DataSource> dataSourceObjectProvider) {
		this.dataSourceObjectProvider = dataSourceObjectProvider;
	}

	@Override
	public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof TenantDetailsService tenantDetailsService) {
			return new InitializingTenantDetailsService(tenantDetailsService, this.dataSourceObjectProvider);
		}
		return bean;
	}

}

/**
 * Prefixes every table with a tenant identifier, so {@code customer} becomes
 * {@code tenant1_customer}. The tenant is fixed for the life of the strategy: one
 * instance belongs to exactly one tenant's mapping context.
 */
record TenantAwareNamingStrategy(String tenant) implements NamingStrategy {

	@Override
	public String getTableName(Class<?> type) {
		return this.tenant + "_" + NamingStrategy.super.getTableName(type);
	}
}

/**
 * Builds and caches one complete Spring Data JDBC stack -- mapping context, converter,
 * data access strategy, aggregate template -- per tenant.
 * <p>
 * A stack <em>per tenant</em> is what makes this work, as opposed to a table name
 * resolved at query time. Spring Data JDBC renders the SQL for an entity exactly once and
 * then caches it, both in {@code SqlGenerator} (every statement is a
 * {@code Lazy<String>}) and in {@code SqlGeneratorSource} (keyed by domain type alone).
 * Whichever tenant happened to issue the first query would otherwise have its table name
 * baked into the cached statement for every tenant after it.
 */
@Component
class TenantJdbcStacks {

	private final Map<String, JdbcAggregateOperations> operationsByTenant = new ConcurrentHashMap<>();

	private final ApplicationContext applicationContext;

	private final NamedParameterJdbcOperations jdbcOperations;

	private final JdbcCustomConversions conversions;

	private final RelationalManagedTypes managedTypes;

	private final JdbcDialect dialect;

	TenantJdbcStacks(ApplicationContext applicationContext, NamedParameterJdbcOperations jdbcOperations,
			JdbcCustomConversions conversions, RelationalManagedTypes managedTypes, JdbcDialect dialect) {
		this.applicationContext = applicationContext;
		this.jdbcOperations = jdbcOperations;
		this.conversions = conversions;
		this.managedTypes = managedTypes;
		this.dialect = dialect;
	}

	JdbcAggregateOperations forTenant(String tenant) {
		return this.operationsByTenant.computeIfAbsent(tenant, this::createOperations);
	}

	private JdbcAggregateOperations createOperations(String tenant) {

		var mappingContext = JdbcConfiguration.createMappingContext(this.managedTypes, this.conversions,
				new TenantAwareNamingStrategy(tenant));

		// the converter needs a RelationResolver and the data access strategy needs the
		// converter,
		// so break the cycle the way AbstractJdbcConfiguration does, with an indirection
		var relationResolver = new DelegatingDataAccessStrategy();
		var converter = JdbcConfiguration.createConverter(mappingContext, this.jdbcOperations, relationResolver,
				this.conversions, this.dialect);
		var dataAccessStrategy = JdbcConfiguration.createDataAccessStrategy(this.jdbcOperations, converter,
				QueryMappingConfiguration.EMPTY, this.dialect);
		relationResolver.setDelegate(dataAccessStrategy);

		return new JdbcAggregateTemplate(this.applicationContext, mappingContext, converter, dataAccessStrategy);
	}

}

/**
 * Hands out a repository proxy that resolves, on every invocation, to the repository
 * built for the tenant bound to the current {@link TenantContext}. Each tenant's
 * repository is an ordinary Spring Data JDBC repository -- query methods, transactions
 * and exception translation all behave as usual -- it just addresses
 * {@code ${tenant}_}-prefixed tables.
 */
class TenantRoutingJdbcRepositoryFactoryBean<T extends Repository<S, ID>, S, ID extends Serializable>
		extends JdbcRepositoryFactoryBean<T, S, ID> {

	private final Map<String, T> repositoriesByTenant = new ConcurrentHashMap<>();

	private @Nullable ListableBeanFactory beanFactory;

	private @Nullable ApplicationEventPublisher publisher;

	private @Nullable T routingRepository;

	TenantRoutingJdbcRepositoryFactoryBean(Class<? extends T> repositoryInterface) {
		super(repositoryInterface);
		// no tenant is bound at startup, so the shared stack must not build a repository
		// of its own
		setLazyInit(true);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		super.setBeanFactory(beanFactory);
		this.beanFactory = (ListableBeanFactory) beanFactory;
	}

	@Override
	public void setApplicationEventPublisher(ApplicationEventPublisher publisher) {
		super.setApplicationEventPublisher(publisher);
		this.publisher = publisher;
	}

	@Override
	@SuppressWarnings("unchecked")
	public T getObject() {

		if (this.routingRepository == null) {
			var repositoryInterface = getObjectType();
			this.routingRepository = (T) Proxy.newProxyInstance(repositoryInterface.getClassLoader(),
					new Class<?>[] { repositoryInterface }, (proxy, method, args) -> switch (method.getName()) {
						case "equals" -> proxy == args[0];
						case "hashCode" -> System.identityHashCode(proxy);
						case "toString" -> "tenant routing " + repositoryInterface.getName();
						default -> invokeForCurrentTenant(method, args);
					});
		}
		return this.routingRepository;
	}

	private Object invokeForCurrentTenant(Method method, Object @Nullable [] args) throws Throwable {
		var repository = this.repositoryFor(TenantContext.getRequiredTenantIdentifier());
		try {
			return method.invoke(repository, args);
		}
		catch (InvocationTargetException ex) {
			throw ex.getTargetException();
		}
	}

	private T repositoryFor(String tenant) {
		return this.repositoriesByTenant.computeIfAbsent(tenant, identifier -> {
			var operations = this.beanFactory.getBean(TenantJdbcStacks.class).forTenant(identifier);
			var factoryBean = new JdbcRepositoryFactoryBean<T, S, ID>(getObjectType());
			factoryBean.setBeanFactory(this.beanFactory);
			factoryBean.setApplicationEventPublisher(this.publisher);
			// Spring normally injects this; a package-private repository interface can
			// only be
			// proxied by the loader that defined it
			factoryBean.setBeanClassLoader(getObjectType().getClassLoader());
			factoryBean.setJdbcAggregateOperations(operations);
			factoryBean.afterPropertiesSet();
			return factoryBean.getObject();
		});
	}

}

@Controller
@ResponseBody
class CustomerController {

	private final CustomerRepository repository;

	CustomerController(CustomerRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/customers")
	Collection<Customer> customers() {
		return repository.findAll();
	}

}

interface CustomerRepository extends ListCrudRepository<Customer, Integer> {

}

@Table
record Customer(@Id int id, String name) {
}
