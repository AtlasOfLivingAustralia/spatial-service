package page

import geb.Page

class SpatialServiceHomePage extends Page {
    static at = { title == "ALA | Login" || title.startsWith("Spatial Service") }

    static content = {
        authModule { module(AuthModule) }
    }
}

