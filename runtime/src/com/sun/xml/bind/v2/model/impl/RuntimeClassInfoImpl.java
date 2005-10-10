package com.sun.xml.bind.v2.model.impl;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.io.IOException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.bind.annotation.XmlLocation;
import com.sun.xml.bind.api.AccessorException;
import com.sun.xml.bind.v2.ClassFactory;
import com.sun.xml.bind.v2.model.annotation.Locatable;
import com.sun.xml.bind.v2.model.core.Adapter;
import com.sun.xml.bind.v2.model.core.PropertyKind;
import com.sun.xml.bind.v2.model.nav.Navigator;
import com.sun.xml.bind.v2.model.runtime.RuntimeClassInfo;
import com.sun.xml.bind.v2.model.runtime.RuntimeElement;
import com.sun.xml.bind.v2.model.runtime.RuntimePropertyInfo;
import com.sun.xml.bind.v2.model.runtime.RuntimeValuePropertyInfo;
import com.sun.xml.bind.v2.runtime.Location;
import com.sun.xml.bind.v2.runtime.Transducer;
import com.sun.xml.bind.v2.runtime.XMLSerializer;
import com.sun.xml.bind.v2.runtime.Name;
import com.sun.xml.bind.v2.runtime.reflect.Accessor;
import com.sun.xml.bind.v2.runtime.reflect.TransducedAccessor;
import com.sun.xml.bind.v2.runtime.unmarshaller.UnmarshallingContext;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
class RuntimeClassInfoImpl extends ClassInfoImpl<Type,Class,Field,Method>
        implements RuntimeClassInfo, RuntimeElement {

    /**
     * If this class has a property annotated with {@link XmlLocation},
     * this field will get the accessor for it.
     *
     * TODO: support method based XmlLocation
     */
    private Accessor<?,Locator> xmlLocationAccessor;

    public RuntimeClassInfoImpl(RuntimeModelBuilder modelBuilder, Locatable upstream, Class clazz) {
        super(modelBuilder, upstream, clazz);
    }

    public final RuntimeClassInfoImpl getBaseClass() {
        return (RuntimeClassInfoImpl)super.getBaseClass();
    }

    @Override
    protected ReferencePropertyInfoImpl createReferenceProperty(PropertySeed<Type,Class,Field,Method> seed) {
        return new RuntimeReferencePropertyInfoImpl(this,seed);
    }

    @Override
    protected AttributePropertyInfoImpl createAttributeProperty(PropertySeed<Type,Class,Field,Method> seed) {
        return new RuntimeAttributePropertyInfoImpl(this,seed);
    }

    @Override
    protected ValuePropertyInfoImpl createValueProperty(PropertySeed<Type,Class,Field,Method> seed) {
        return new RuntimeValuePropertyInfoImpl(this,seed);
    }

    @Override
    protected ElementPropertyInfoImpl createElementProperty(PropertySeed<Type,Class,Field,Method> seed) {
        return new RuntimeElementPropertyInfoImpl(this,seed);
    }

    @Override
    protected MapPropertyInfoImpl createMapProperty(PropertySeed<Type,Class,Field,Method> seed) {
        return new RuntimeMapPropertyInfoImpl(this,seed);
    }


    @Override
    public List<? extends RuntimePropertyInfo> getProperties() {
        return (List<? extends RuntimePropertyInfo>)super.getProperties();
    }

    @Override
    public RuntimePropertyInfo getProperty(String name) {
        return (RuntimePropertyInfo)super.getProperty(name);
    }


    public void link() {
        super.link();
        getTransducer();    // populate the transducer
    }

    private Accessor<?,Map<QName,String>> attributeWildcardAccessor;

    public <B> Accessor<B,Map<QName,String>> getAttributeWildcard() {
        for( RuntimeClassInfoImpl c=this; c!=null; c=c.getBaseClass() ) {
            if(c.attributeWildcard!=null) {
                if(c.attributeWildcardAccessor==null)
                    c.attributeWildcardAccessor = c.createAttributeWildcardAccessor();
                return (Accessor<B,Map<QName,String>>)c.attributeWildcardAccessor;
            }
        }
        return null;
    }

    private boolean computedTransducer = false;
    private Transducer xducer = null;

    public Transducer getTransducer() {
        if(!computedTransducer) {
            computedTransducer = true;
            xducer = calcTransducer();
        }
        return xducer;
    }

    /**
     * Creates a transducer if this class is bound to a text in XML.
     */
    private Transducer calcTransducer() {
        RuntimeValuePropertyInfo valuep=null;
        for (RuntimeClassInfoImpl ci = this; ci != null; ci = ci.getBaseClass()) {
            for( RuntimePropertyInfo pi : ci.getProperties() )
                if(pi.kind()==PropertyKind.VALUE) {
                    valuep = (RuntimeValuePropertyInfo)pi;
                } else {
                    // this bean has something other than a value
                    return null;
                }
        }
        if(valuep==null)
            return null;
        if( !valuep.getTarget().isSimpleType() )
            return null;    // if there's an error, recover from it by returning null.
        
        return new TransducerImpl(getClazz(),TransducedAccessor.get(valuep));
    }

    /**
     * Creates
     */
    private Accessor<?,Map<QName,String>> createAttributeWildcardAccessor() {
        assert attributeWildcard!=null;
        return ((RuntimePropertySeed)attributeWildcard).getAccessor();
    }

    @Override
    protected RuntimePropertySeed createFieldSeed(Field field) {
        Accessor.FieldReflection acc;
        if(Modifier.isStatic(field.getModifiers()))
            acc = new Accessor.ReadOnlyFieldReflection(field);
        else
            acc = new Accessor.FieldReflection(field);

        return new RuntimePropertySeed(
            super.createFieldSeed(field),
                acc );
    }

    @Override
    public RuntimePropertySeed createAccessorSeed(Method getter, Method setter) {
        return new RuntimePropertySeed(
            super.createAccessorSeed(getter,setter),
            new Accessor.GetterSetterReflection(getter,setter) );
    }

    @Override
    protected void checkFieldXmlLocation(Field f) {
        if(reader().hasFieldAnnotation(XmlLocation.class,f))
            // TODO: check for XmlLocation signature
            // TODO: check a collision with the super class
            xmlLocationAccessor = new Accessor.FieldReflection<Object,Locator>(f);
    }

    public Accessor<?,Locator> getLocatorField() {
        return xmlLocationAccessor;
    }

    static final class RuntimePropertySeed implements PropertySeed<Type,Class,Field,Method> {
        /**
         * @see #getAccessor()
         */
        private final Accessor acc;

        private final PropertySeed<Type,Class,Field,Method> core;

        public RuntimePropertySeed(PropertySeed<Type,Class,Field,Method> core, Accessor acc) {
            this.core = core;
            this.acc = acc;
        }

        public String getName() {
            return core.getName();
        }

        public <A extends Annotation> A readAnnotation(Class<A> annotationType) {
            return core.readAnnotation(annotationType);
        }

        public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
            return core.hasAnnotation(annotationType);
        }

        public Type getRawType() {
            return core.getRawType();
        }

        public Location getLocation() {
            return core.getLocation();
        }

        public Locatable getUpstream() {
            return core.getUpstream();
        }

        public Accessor getAccessor() {
            return acc;
        }
    }


    
    /**
     * {@link Transducer} implementation used when this class maps to PCDATA in XML.
     *
     * TODO: revisit the exception handling
     */
    private static final class TransducerImpl<BeanT> implements Transducer<BeanT> {
        private final TransducedAccessor<BeanT> xacc;
        private final Class<BeanT> ownerClass;

        public TransducerImpl(Class<BeanT> ownerClass,TransducedAccessor<BeanT> xacc) {
            this.xacc = xacc;
            this.ownerClass = ownerClass;
        }

        public boolean useNamespace() {
            return xacc.useNamespace();
        }

        public boolean isDefault() {
            return false;
        }

        public void declareNamespace(BeanT bean, XMLSerializer w) throws AccessorException {
            try {
                xacc.declareNamespace(bean,w);
            } catch (SAXException e) {
                throw new AccessorException(e);
            }
        }

        public CharSequence print(BeanT bean) throws AccessorException {
            try {
                return xacc.print(bean);
            } catch (SAXException e) {
                throw new AccessorException(e);
            }
        }

        public BeanT parse(CharSequence lexical) throws AccessorException, SAXException {
            UnmarshallingContext ctxt = UnmarshallingContext.getInstance();
            BeanT inst;
            if(ctxt!=null)
                inst = (BeanT)ctxt.createInstance(ownerClass);
            else
                // when this runs for parsing enum constants,
                // there's no UnmarshallingContext.
                inst = ClassFactory.create(ownerClass);

            xacc.parse(inst,lexical);
            return inst;
        }

        public void writeText(XMLSerializer w, BeanT o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
            xacc.writeText(w,o,fieldName);
        }

        public void writeLeafElement(XMLSerializer w, Name tagName, BeanT o, String fieldName) throws IOException, SAXException, XMLStreamException, AccessorException {
            xacc.writeLeafElement(w,tagName,o,fieldName);
        }
    }
}
