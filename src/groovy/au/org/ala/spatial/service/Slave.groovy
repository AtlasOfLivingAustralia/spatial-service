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
        limits.each { name, lim ->
            if (!canAdd) {
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

                //add if it fits
                if ((tsize != null && tsize + size < lim.total) ||
                        (lim.pool.size() == 0 && size < lim.total)) {
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
        limits.each { name, lim ->
            if (lim.tasks.containsKey(task.id)) {
                found = true
            }
        }

        found
    }

    def remove(task) {
        def removed = false
        limits.each { name, lim ->
            if (lim.tasks.containsKey(task.id)) {
                removed = true
                lim.tasks.remove(task.id)
            }
        }

        removed
    }

    def tasks() {
        def list = []
        limits.each { name, lim ->
            list.addAll(lim.tasks.entrySet())
        }
        list
    }
}
