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

import bacmman.plugins.Hint;
import bacmman.utils.JSONSerializable;
import java.util.ArrayList;
import java.util.function.Predicate;
import javax.swing.tree.MutableTreeNode;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
/**
 *
 * @author Jean Ollion
 */

public interface Parameter<P extends Parameter<P>> extends MutableTreeNode, JSONSerializable, Hint {
    Logger logger = LoggerFactory.getLogger(Parameter.class);
    ArrayList<Parameter> getPath();
    void setContentFrom(Parameter other);
    boolean sameContent(Parameter other);
    P duplicate();
    String getName();
    void setName(String name);
    String toStringFull();
    <T extends P> T setHint(String tip);
    boolean isValid();
    boolean isEmphasized();
    P setEmphasized(boolean isEmphasized);
    P addValidationFunction(Predicate<P> isValid);
}