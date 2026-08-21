package com.example.service;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import javax.sql.DataSource;
import javax.sql.XADataSource;

class Hints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.proxies().registerJdkProxy(XADataSource.class, DataSource.class);
    }
}
