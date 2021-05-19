package page

import geb.Page

class HomePage extends Page {
    static at = { title == "ALA | Login" || title.startsWith("Spatial Service") }

    static content = {
        authModule { module(AuthModule) }

        subTitle {$("h1").text()}

        taskTable { $("table[name='tasks']") }
    }

    void clickLink(name){
        $("a",text: name).click()
    }

    def numberOfTasks() {
        taskTable.find("tbody tr").size() - 1
    }
}

