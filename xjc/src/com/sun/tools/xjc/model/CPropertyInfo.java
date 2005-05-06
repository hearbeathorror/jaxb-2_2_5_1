package com.sun.tools.xjc.model;

import java.util.Collection;

import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JJavaName;
import com.sun.tools.xjc.generator.bean.field.FieldRenderer;
import com.sun.tools.xjc.model.nav.NClass;
import com.sun.tools.xjc.model.nav.NType;
import com.sun.xml.bind.v2.NameConverter;
import com.sun.xml.bind.v2.model.core.PropertyInfo;
import com.sun.xml.bind.v2.runtime.Util;

import org.xml.sax.Locator;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class CPropertyInfo implements PropertyInfo<NType,NClass>, CCustomizable {

    @XmlTransient
    private CClassInfo parent;
    private final String privateName;
    private final String publicName;
    private final boolean isCollection;

    @XmlTransient
    public final Locator locator;

    /**
     * Javadoc for this property. Must not be null.
     */
    public String javadoc="";

    /**
     * Specifies how the field is generated by the backedn.
     */
    @XmlJavaTypeAdapter(Util.ToStringAdapter.class)
    public FieldRenderer realization;

    /**
     * If non-null, keeps the default value in Java representation.
     *
     * If {@link #isCollection} is true, this field is always null,
     * for we don't handle default values for a list.
     */
    @XmlJavaTypeAdapter(Util.ToStringAdapter.class)
    public JExpression defaultValue;

    private final CCustomizations customizations;

    protected CPropertyInfo(String name, boolean collection,
                            CCustomizations customizations, Locator locator) {
        this.publicName = name;
        String n = NameConverter.standard.toVariableName(name);
        if(!JJavaName.isJavaIdentifier(n))
            n = '_'+n;  // avoid colliding with the reserved names like 'abstract'.
        this.privateName = n;

        this.isCollection = collection;
        this.locator = locator;
        if(customizations==null)
            this.customizations = CCustomizations.EMPTY;
        else
            this.customizations = customizations;
    }

    // called from CClassInfo when added
    final void setParent( CClassInfo parent ) {
        assert this.parent==null;
        assert parent!=null;
        this.parent = parent;
        customizations.setParent(parent.model,this);
    }

    public CTypeInfo parent() {
        return parent;
    }

    public Locator getLocator() {
        return locator;
    }

    public abstract CAdapter getAdapter();

    /**
     * Name of the property.
     *
     * <p>
     * This method is implemented to follow the contract of
     * {@link PropertyInfo#getName()}, and therefore it always
     * returns the name of the annotated field.
     * <p>
     * This name is normally not useful for the rest of XJC,
     * which usually wants to access the "public name" of the property.
     * A "public name" of the property is a name like "FooBar" which
     * is used as a seed for generating the accessor methods.
     * This is the name controlled by the schema customization via users.
     *
     * <p>
     * If the caller is calling this method statically, it's usually
     * the sign of a mistake. Use {@link #getName(boolean)} method instead,
     * which forces you to think about which name you want to get.
     *
     * @deprecated
     *      marked as deprecated so that we can spot the use of this method.
     * 
     * @see #getName(boolean)
     */
    public String getName() {
        return getName(false);
    }

    /**
     * Gets the name of the property.
     *
     * @param isPublic
     *      if true, this method returns a name like "FooBar", which
     *      should be used as a seed for generating user-visible names
     *      (such as accessors like "getFooBar".)
     *
     *      <p>
     *      if false, this method returns the "name of the property"
     *      as defined in the j2s side of the spec. This name is usually
     *      something like "fooBar", which often corresponds to the XML
     *      element/attribute name of this property (for taking advantage
     *      of annotation defaulting as much as possible)
     */
    public String getName(boolean isPublic) {
        return isPublic?publicName:privateName;

    }

    public String displayName() {
        return parent.toString()+'#'+getName(false);
    }

    public boolean isCollection() {
        return isCollection;
    }


    public abstract Collection<? extends CTypeInfo> ref();

    /**
     * Returns true if this property is "unboxable".
     *
     * <p>
     * In general, a property often has to be capable of representing null
     * to indicate the absence of the value. This requires properties
     * to be generated as <tt>@XmlElement Float f</tt>, not as
     * <tt>@XmlElement float f;</tt>. But this is slow.
     *
     * <p>
     * Fortunately, there are cases where we know that the property can
     * never legally be absent. When this condition holds we can generate
     * the optimized "unboxed form".
     *
     * <p>
     * The exact such conditions depend
     * on the kind of properties, so refer to the implementation code
     * for the details.
     *
     * <p>
     * This method returns true when the property can be generated
     * as "unboxed form", false otherwise.
     *
     * <p>
     * When this property is a collection, this method returns true
     * if items in the collection is unboxable. Obviously, the collection
     * itself is always a reference type.
     */
    public boolean isUnboxable() {
        Collection<? extends CTypeInfo> ts = ref();
        if(ts.size()>1)
            // if the property is heterogeneous, no way.
            return false;

        CTypeInfo t = ts.iterator().next();
        // only a primitive type is eligible.
        return t.getType().isBoxedType();
    }

    public CCustomizations getCustomizations() {
        return customizations;
    }
}
