package au.org.ala.spatial.util

import org.apache.commons.io.filefilter.IOFileFilter

class BaseFileNameInDirectoryFilter implements IOFileFilter {

    private final String baseFileName
    private final File parentDir
    private final List<File> excludedFiles

    BaseFileNameInDirectoryFilter(String baseFileName, File parentDir, List<File> excludedFiles) {
        this.baseFileName = baseFileName
        this.parentDir = parentDir
        this.excludedFiles = new ArrayList<File>(excludedFiles)
    }

    @Override
    boolean accept(File file) {
        if (excludedFiles.contains(file)) {
            return false
        }
        return file.getParentFile() == parentDir && file.getName().startsWith(baseFileName)
    }

    @Override
    boolean accept(File dir, String name) {
        if (excludedFiles.contains(new File(dir, name))) {
            return false
        }
        return dir == parentDir && name.startsWith(baseFileName)
    }

}
