package org.flymine.modelproduction.uml;

// Most of this code originated in the ArgoUML project, which carries
// the following copyright
//
// Copyright (c) 1996-2001 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies.  This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free. The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason.  IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

import ru.novosoft.uml.xmi.XMIReader;
import ru.novosoft.uml.foundation.core.*;
import ru.novosoft.uml.foundation.data_types.MMultiplicity;
import ru.novosoft.uml.model_management.MPackage;
import ru.novosoft.uml.foundation.extension_mechanisms.MTaggedValue;

import java.util.Iterator;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.io.InputStream;
import org.xml.sax.InputSource;

import org.apache.log4j.Logger;

import org.flymine.modelproduction.AbstractModelParser;
import org.flymine.util.StringUtil;
import org.flymine.metadata.*;

/**
 * Translates a model representation in XMI to FlyMine metadata (Java)
 *
 * @author Mark Woodbridge
 */
public class XmiParser extends AbstractModelParser
{
    protected static final Logger LOG = Logger.getLogger(XmiParser.class);

    private Collection keys;
    private Set attributes, references, collections;
    private Set classes = new LinkedHashSet();

    /**
     * @see ModelParser#process
     * @throws Exception
     */
    public Model process(InputStream is) throws Exception {
        recurse(new XMIReader().parse(new InputSource(is)));
        return new Model("testmodel", classes);
    }

    /**
     * Adds a field (UML: attribute)
     * @param attr the attribute
     */
    protected void generateAttribute(MAttribute attr) {
        String name = attr.getName();
        String type = qualify(attr.getType().getName());
        boolean primaryKey = keys.contains(attr.getName());
        attributes.add(new AttributeDescriptor(name, primaryKey, type));
    }

    /**
    * Adds a class or interface (UML: classifier)
    * @param cls the classifier
    */
    protected void generateClassifier(MClassifier cls) {
        String name = qualified(cls);
        String extend = generateGeneralization(cls.getGeneralizations());
        String implement;
        boolean isInterface;
        if (cls instanceof MClass) {
            isInterface = false;
            implement = generateSpecification((MClass) cls);
        } else {
            isInterface = true;
            implement = null;
        }

        keys = getKeys(cls);

        attributes = new LinkedHashSet();
        Iterator strIter = getAttributes(cls).iterator();
        while (strIter.hasNext()) {
            generateAttribute((MAttribute) strIter.next());
        }
        
        references = new LinkedHashSet();
        collections = new LinkedHashSet();
        Iterator endIter = cls.getAssociationEnds().iterator();
        while (endIter.hasNext()) {
            generateAssociationEnd(((MAssociationEnd) endIter.next()).getOppositeEnd());
        }

        classes.add(new ClassDescriptor(name, extend, implement, isInterface, attributes,
                                        references, collections));
    }

    /**
    * Adds a reference or collection of references to business objects (UML: association)
    * @param ae the local end of the association
    */
    protected void generateAssociationEnd(MAssociationEnd ae) {
        if (ae.isNavigable()) {
            String name = nameEnd(ae);
            String referencedType = qualified(ae.getType());
            MAssociationEnd ae2 = ae.getOppositeEnd();
            String reverseReference = ae2.isNavigable() ? nameEnd(ae2) : null;
            boolean primaryKey = keys.contains(name);
            MMultiplicity m = ae.getMultiplicity();
            if (MMultiplicity.M1_1.equals(m) || MMultiplicity.M0_1.equals(m)) {
                references.add(new ReferenceDescriptor(name, primaryKey, referencedType,
                                                       reverseReference));
            } else {
                boolean ordered = ae.getOrdering() != null
                    && !ae.getOrdering().getName().equals("unordered");
                collections.add(new CollectionDescriptor(name, primaryKey, referencedType,
                                                         reverseReference, ordered));
            }
        }
    }

    //=================================================================

    private void recurse(MNamespace ns) {
        Iterator ownedElements = ns.getOwnedElements().iterator();
        while (ownedElements.hasNext()) {
            MModelElement me = (MModelElement) ownedElements.next();
            if (me instanceof MPackage) {

   recurse((MNamespace) me);
            }
            if (me instanceof MClass && isBusinessObject((MClassifier) me)) {
                generateClassifier((MClass) me);
            }
            if (me instanceof MInterface && isBusinessObject((MClassifier) me)) {
                generateClassifier((MInterface) me);
            }
        }
    }

    private String generateSpecification(MClass cls) {
        Collection realizations = getSpecifications(cls);
        if (realizations == null || realizations.size() == 0) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        Iterator clsEnum = realizations.iterator();
        while (clsEnum.hasNext()) {
            MInterface i = (MInterface) clsEnum.next();
            sb.append(qualified(i));
            if (clsEnum.hasNext()) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String generateGeneralization(Collection generalizations) {
        if (generalizations == null || generalizations.size() == 0) {
            return null;
        }
        Collection classes = new LinkedHashSet();
        Iterator enum = generalizations.iterator();
        while (enum.hasNext()) {
            MGeneralization g = (MGeneralization) enum.next();
            MGeneralizableElement ge = g.getParent();
            if (ge != null) {
                classes.add(ge);
            }
        }
        return generateClassSet(classes);
    }

    private String generateClassSet(Collection classifiers) {
        if (classifiers == null) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        Iterator clsEnum = classifiers.iterator();
        while (clsEnum.hasNext()) {
            MClassifier cls = (MClassifier) clsEnum.next();
            sb.append(qualified(cls));
            if (clsEnum.hasNext()) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String qualified(MClassifier cls) {
        return getPackagePath(cls) + "." + cls.getName();
    }

    private Collection getSpecifications(MClassifier cls) {
        Collection result = new LinkedHashSet();
        Iterator depIterator = cls.getClientDependencies().iterator();

        while (depIterator.hasNext()) {
            MDependency dep = (MDependency) depIterator.next();
            if ((dep instanceof MAbstraction)
                && dep.getStereotype() != null
                && dep.getStereotype().getName() != null
                && dep.getStereotype().getName().equals("realize")) {
                MInterface i = (MInterface) dep.getSuppliers().toArray()[0];
                result.add(i);
            }
        }
        return result;
    }

    private Collection getAttributes(MClassifier classifier) {
        Collection result = new LinkedHashSet();
        Iterator features = classifier.getFeatures().iterator();
        while (features.hasNext()) {
            MFeature feature = (MFeature) features.next();
            if (feature instanceof MAttribute) {
                result.add(feature);
            }
        }
        return result;
    }

   private String getPackagePath(MClassifier cls) {
        String packagePath = cls.getNamespace().getName();
        MNamespace parent = cls.getNamespace().getNamespace();
        while (parent != null) {
            packagePath = parent.getName() + "." + packagePath;
            parent = parent.getNamespace();
        }
        return packagePath;
    }

    private Collection getKeys(MClassifier cls) {
        Set keyFields = new LinkedHashSet();
        Collection tvs = cls.getTaggedValues();
        if (tvs != null && tvs.size() > 0) {
            Iterator iter = tvs.iterator();
            while (iter.hasNext()) {
                MTaggedValue tv = (MTaggedValue) iter.next();
                if (tv.getTag().equals("key")) {
                    keyFields.addAll(StringUtil.tokenize(tv.getValue()));
                }
            }
        }
        Iterator parents = cls.getGeneralizations().iterator();
        if (parents.hasNext()) {
            keyFields.addAll(getKeys((MClassifier) ((MGeneralization) parents.next()).getParent()));
        }
        return keyFields;
    }

    private String nameEnd(MAssociationEnd ae) {
        String name = ae.getName();
        if (name == null || name.length() == 0) {
            name = ae.getType().getName();
            MMultiplicity m = ae.getMultiplicity();
            if (!MMultiplicity.M1_1.equals(m) && !MMultiplicity.M0_1.equals(m)) {
                name = StringUtil.pluralise(name);
            }
        }
        return StringUtil.decapitalise(name);
    }

    private String qualify(String type) {
        if (type.equals("String")) {
            return "java.lang.String";
        }
        return type;
    }

    private boolean isBusinessObject(MClassifier cls) {
        String name = cls.getName();
        if (name == null || name.length() == 0
            || name.equals("void") || name.equals("char") || name.equals("byte")
            || name.equals("short") || name.equals("int") || name.equals("long")
            || name.equals("boolean") || name.equals("float") || name.equals("double")) {
            return false;
        }

        String packagePath = getPackagePath(cls);

        if (packagePath.endsWith("java.lang") || packagePath.endsWith("java.util")) {
            return false;
        }

        return true;
    }
}
