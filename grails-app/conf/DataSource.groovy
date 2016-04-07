//This makes use of layersdb connection that is used by layers-store
// and found at /data/layers-store/config/layers-store-data-config.xml
// do not use create-drop
dataSource {
//    pooled = true
//    driverClassName = "org.postgresql.Driver"
//    dialect = org.hibernate.dialect.PostgreSQL9Dialect
//    dbCreate = "update" // one of 'create', 'update', 'validate', ''
//    url = "jdbc:postgresql://localhost/layersdb"
//    username = "postgres"
//    password = "postgres"
}

//dataSource {
//    pooled = true
//    driverClassName = "org.postgresql.Driver"
//    dialect = org.hibernate.dialect.PostgreSQL9Dialect
//
//    username = "postgres"
//    password = "postgres"
//}
//hibernate {
//    cache.use_second_level_cache = true
//    cache.use_query_cache = true
//    cache.provider_class = 'org.hibernate.cache.EhCacheProvider'
//}
//
//// environment specific settings
environments {
    development {
        dataSource {
            dbCreate = "update" //"create-drop" // one of 'create', 'create-drop', 'update', 'validate', ''
        }
    }
    test {
        dataSource {
            dbCreate = "update"
            url = "jdbc:h2:mem:testDb;MVCC=TRUE;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
        }
    }
    production {
        dataSource {
            dbCreate = "update" //"create-drop" // one of 'create', 'create-drop', 'update', 'validate', ''
            logSql = false
        }
    }
}
