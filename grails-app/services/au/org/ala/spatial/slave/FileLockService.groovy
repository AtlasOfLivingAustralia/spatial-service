/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.slave

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * When a file can or is to be updated, lock the file to prevent more than one process from changing or creating the file
 * at the same time.
 *
 * TODO: add 'readOnly' in addition to the current 'editing' locking.
 */
class FileLockService {
    Map filesList = new ConcurrentHashMap()
    Map<CountDownLatch, Map> locks = new ConcurrentHashMap()

    final Object lock = new Object()

    //tasks that write to files need to lock them to prevent conflicts
    // returns null to go continue, release(files) still needs to be called to release the locks
    // returns object that will be notified when the locked files are available, 
    //          e.g. synchronized (object) { object.wait() }
    CountDownLatch lock(List files, Task task) {
        CountDownLatch filesLock = null
        synchronized (lock) {
            //identify files in use
            int countLocked = 0
            files.each { file ->
                if (filesList.containsKey(file)) {
                    log.debug 'locking file:' + file
                    countLocked++
                }
            }

            if (countLocked > 0) {
                //create an object for locking
                filesLock = new CountDownLatch(1)
                locks.put(filesLock, [task: task, files: files])
                log.debug 'task: ' + task.id + ' waiting for files to be released'
            } else {
                //create a lock on these files
                files.each { file ->
                    filesList.putAt(file, task)
                }
            }
        }

        filesLock
    }

    void release(files) {
        synchronized (lock) {
            files.each { file ->
                if (filesList.containsKey(file)) {
                    filesList.remove(file)
                }
            }

            // find locks that can be released
            List unlocked = []
            locks.each { k, v ->
                Task nextTask = v.task
                def nextFiles = v.files
                //signal k if all v are available
                def countLocked = 0
                nextFiles.each { f ->
                    if (filesList.containsKey(f)) {
                        countLocked++
                    }
                }

                if (countLocked == 0) {
                    //create a lock on these files with the next task
                    nextFiles.each { f ->
                        filesList.putAt(f, nextTask)
                    }

                    unlocked.add(k)
                    k.countDown()

                    log.debug 'releasing file lock on task:' + nextTask.id
                }
            }
            unlocked.each {
                locks.remove(it)
            }
        }
    }
}
