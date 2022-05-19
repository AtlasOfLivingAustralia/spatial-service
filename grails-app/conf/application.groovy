grails.plugin.databasemigration.changelogFileName = 'changelog.xml'
grails.plugin.databasemigration.updateOnStart = true
grails.plugin.databasemigration.updateOnStartFileName = 'changelog.xml'

// We can limit the context where the db migration run
// https://liquibase.org/blog/contexts-vs-labels
// typically to skip during test or dev
// grails.plugin.databasemigration.updateOnStartContexts = ['context1,context2']

// If extra logs are needed for liquibase
// logging.level.liquibase=on
// logging.level.liquibase.executor=on
