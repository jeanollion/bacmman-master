/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package bacmman.configuration.parameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.swing.JFileChooser;
import org.json.simple.JSONArray;
import bacmman.utils.JSONUtils;
import bacmman.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class FileChooser extends ParameterImpl<FileChooser> implements Listenable<FileChooser> {
    protected String[] selectedFiles=new String[0];
    boolean allowNoSelection = true;
    protected FileChooserOption option = FileChooserOption.DIRECTORIES_ONLY;
    boolean relativePath = true;
    public FileChooser(String name, FileChooserOption option) {
        this(name, option, true);
    }
    public FileChooser(String name, FileChooserOption option, boolean allowNoSelection) {
        super(name);
        this.option=option;
        this.allowNoSelection=allowNoSelection;
    }
    public FileChooser setRelativePath(boolean relativePath) {
        this.relativePath=relativePath;
        return this;
    }
    public String[] getSelectedFilePath() {
        return selectedFiles;
    }
    
    public String getFirstSelectedFilePath() {
        if (selectedFiles.length==0) return null;
        return selectedFiles[0];
    }
    
    public void setSelectedFilePath(String... filePath) {
        if (filePath==null) selectedFiles = new String[0];
        else selectedFiles=filePath;
        fireListeners();
    }
    public void setSelectedFiles(File... filePath ) {
        selectedFiles = new String[filePath.length];
        int i = 0;
        for (File f : filePath) selectedFiles[i++]=f.getAbsolutePath();
        fireListeners();
    }
    @Override 
    public boolean isValid() {
        if (!super.isValid()) return false;
        if (selectedFiles.length==0 && !allowNoSelection) return false;
        else if (selectedFiles.length>0) { // check that all files exist
            if (relativePath && ParameterUtils.getExperiment(this).getPath()==null) return true; // can't test if files exist
            if (Arrays.stream(selectedFiles).anyMatch(f -> !Files.exists(Paths.get(f)))) return false;
        }
        return true;
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof FileChooser) {
            if (((FileChooser)other).selectedFiles.length==selectedFiles.length) {
                for (int i =0; i<selectedFiles.length; i++) { 
                    if ((selectedFiles[i] ==null && ((FileChooser)other).selectedFiles[i]!=null) || ( selectedFiles[i] !=null && !selectedFiles[i].equals(((FileChooser)other).selectedFiles[i]))) {
                        logger.trace("FileChoose {}!={}: difference in selected files : {} vs {}", this, other, selectedFiles, ((FileChooser)other).selectedFiles);
                        return false;
                    }
                }
                return true;
            } else {
                logger.trace("FileChoose {}!={}: # of files : {} vs {}", this, other, selectedFiles.length, ((FileChooser)other).selectedFiles.length);
                return false;
            }
        } else return false;
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof FileChooser) {
            //this.option=((FileChooser)other).option;
            this.selectedFiles=Arrays.copyOf(((FileChooser)other).selectedFiles, ((FileChooser)other).selectedFiles.length);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    @Override public String toString() {return name+" :"+Utils.getStringArrayAsString(selectedFiles);}
    
    public FileChooserOption getOption() {return option;}
    
    @Override public FileChooser duplicate() {
        FileChooser res = new FileChooser(name, option, allowNoSelection).setRelativePath(relativePath);
        res.setListeners(listeners);
        res.addValidationFunction(additionalValidation);
        return res;
    }

    @Override
    public Object toJSONEntry() {
        if (!relativePath) {
            return JSONUtils.toJSONArray(selectedFiles);
        } else {
            Path refPath = ParameterUtils.getExperiment(this).getPath();
            if (refPath==null) return JSONUtils.toJSONArray(selectedFiles);
            String[] relPath = Arrays.stream(selectedFiles).map(p -> refPath.relativize(Paths.get(p)).toString()).toArray(String[]::new);
            //logger.debug("save path: {}, ref path: {}, files: {} relativized: {}", name, refPath, selectedFiles, relPath);
            return JSONUtils.toJSONArray(relPath);
        }
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        selectedFiles = JSONUtils.fromStringArray((JSONArray)jsonEntry);
        if (relativePath && selectedFiles.length>0) {
            Path refPath = ParameterUtils.getExperiment(this).getPath();
            //logger.debug("init files {} -> {} (refPath: {})", name, selectedFiles, refPath);
            selectedFiles = Arrays.stream(selectedFiles).map(p -> {
                if (refPath==null) return p;
                String rel = refPath.resolve(Paths.get(p)).normalize().toFile().getAbsolutePath();
                if (rel==null) return p;
                else return rel;
            }).toArray(String[]::new);
            //logger.debug("init files {} after relativize -> {}", name, selectedFiles);
        }
    }
    
    public enum FileChooserOption {
        DIRECTORIES_ONLY(JFileChooser.DIRECTORIES_ONLY, false), 
        FILES_ONLY(JFileChooser.FILES_ONLY, true),
        FILE_ONLY(JFileChooser.FILES_ONLY, false),
        FILES_AND_DIRECTORIES(JFileChooser.FILES_AND_DIRECTORIES, true),
        FILE_OR_DIRECTORY(JFileChooser.FILES_AND_DIRECTORIES, false);
        private final int option;
        private final boolean multipleSelection;
        FileChooserOption(int option, boolean multipleSelection) {
            this.option=option;
            this.multipleSelection=multipleSelection;
        }
        public int getOption() {return option;}
        public boolean getMultipleSelectionEnabled(){return multipleSelection;}
    }
}
