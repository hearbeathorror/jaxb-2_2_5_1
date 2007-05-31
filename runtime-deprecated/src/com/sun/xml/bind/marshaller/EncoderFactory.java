/*
 * @(#)$Id: EncoderFactory.java,v 1.2.6.1 2007-05-31 22:00:05 ofung Exp $
 */

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.xml.bind.marshaller;

import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Creates {@link java.nio.charset.CharsetEncoder} from a charset name.
 * 
 * Fixes a MS1252 handling bug in JDK1.4.2.
 * 
 * <p>
 * No generated code is directly depending on this, so we can potentially
 * remove this in future.
 * 
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
class EncoderFactory {
    static CharsetEncoder createEncoder( String encodin ) {
        Charset cs = Charset.forName(System.getProperty("file.encoding"));
        CharsetEncoder encoder = cs.newEncoder();
        
        if( cs.getClass().getName().equals("sun.nio.cs.MS1252") ) {
            try {
                // at least JDK1.4.2_01 has a bug in MS1252 encoder.
                // specifically, it returns true for any character.
                // return a correct encoder to workaround this problem
                
                // statically binding to MS1252Encoder will cause a Link error
                // (at least in IBM JDK1.4.1)
                Class ms1252encoder = Class.forName("com.sun.xml.bind.marshaller.MS1252Encoder");
                Constructor c = ms1252encoder.getConstructor(new Class[]{
                    Charset.class
                });
                return (CharsetEncoder)c.newInstance(new Object[]{cs});
            } catch( Throwable t ) {
                // if something funny happens, ignore it and fall back to
                // a broken MS1252 encoder. It's probably still better
                // than choking here.
                return encoder;
            }
        }
        
        return encoder;
    }
}
