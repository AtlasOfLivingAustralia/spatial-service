// Place your Spring DSL code here
beans = {
    groovySql(groovy.sql.Sql, ref('dataSource'))
}
