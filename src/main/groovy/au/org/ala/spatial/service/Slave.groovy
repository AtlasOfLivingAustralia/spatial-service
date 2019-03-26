package au.org.ala.spatial.service

class Slave {

    // slave base url
    String url

    // date/time created
    Date created = new Date(System.currentTimeMillis())

    // capabilities
    Map capabilities

    // limits
    Map limits

    // key
    String key


    def canAdd(task) {
        add(task, true)
    }

    def add(task, test = false) {
        def canAdd = false
        //try to add it
        limits.queue.each { name, lim ->
            //limit tasks by priority
            def priority = getPriority(task)
            if (!canAdd && priority >= (lim?.minPriority ?: 0)) {
                //calc current size 
                def size = 0
                lim.tasks.each { id, t ->
                    def sz = lim.pool.get(t.name)
                    if (sz == null) {
                        size++
                    } else {
                        size += sz
                    }
                }

                //find a pool item that matches task.name
                def tsize = lim.pool.get(task.name)

                // is there a user or admin size?
                if (tsize == null) {
                    def spec = capabilities.get(task.name)
                    if (spec != null) {
                        tsize = lim.pool.get(spec?.private?.public != false ? "user" : "admin")
                    }
                }

                // use default size
                if (tsize == null) {
                    tsize = 1
                }

                //add if it fits
                if (tsize + size < lim.total) {
                    //add
                    if (!test) {
                        lim.tasks.put(task.id, task)
                    }

                    canAdd = true
                }
            }
        }

        canAdd
    }

    def hasTask(task) {
        def found = false
        limits.queue.each { name, lim ->
            if (lim.tasks.containsKey(task.id)) {
                found = true
            }
        }

        found
    }

    def remove(task) {
        def removed = false
        limits.queue.each { name, lim ->
            if (lim.tasks.containsKey(task.id)) {
                removed = true
                lim.tasks.remove(task.id)
            }
        }

        removed
    }

    def tasks() {
        def list = []
        limits.queue.each { name, lim ->
            list.addAll(lim.tasks.entrySet())
        }
        list
    }

    def getPriority(task) {
        def priority = limits.priority?.getAt(task.name)
        if (!priority) {
//            if (!task?.spec?.private?.public) {
//                //private tasks are lower priority
//                priority = 49
//            } else {
            //default priority
            priority = 50
//            }
        }
        priority
    }
}
