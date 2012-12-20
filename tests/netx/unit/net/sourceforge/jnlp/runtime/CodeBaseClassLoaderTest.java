/* CodeBaseClassLoaderTest.java
Copyright (C) 2012 Red Hat, Inc.

This file is part of IcedTea.

IcedTea is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License as published by
the Free Software Foundation, version 2.

IcedTea is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with IcedTea; see the file COPYING.  If not, write to
the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version.
 */
package net.sourceforge.jnlp.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Locale;

import net.sourceforge.jnlp.JNLPFile;
import net.sourceforge.jnlp.NullJnlpFileException;
import net.sourceforge.jnlp.ResourcesDesc;
import net.sourceforge.jnlp.SecurityDesc;
import net.sourceforge.jnlp.ServerAccess;
import net.sourceforge.jnlp.runtime.JNLPClassLoader.CodeBaseClassLoader;
import net.sourceforge.jnlp.annotations.Bug;
import net.sourceforge.jnlp.annotations.Remote;
import org.junit.AfterClass;
import org.junit.Assert;

import org.junit.Test;

public class CodeBaseClassLoaderTest {

    private static final URL JAR_URL;
    private static final URL CODEBASE_URL;

    static {
        try {
            JAR_URL = new URL("http://icedtea.classpath.org/netx/about.jar");
            CODEBASE_URL = new URL("http://icedtea.classpath.org/netx/");
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private class DummyJNLPFile extends JNLPFile {

        final boolean haveSecurity;

        public DummyJNLPFile(boolean haveSecurity) {
            this.haveSecurity = haveSecurity;
        }

        @Override
        public ResourcesDesc getResources() {
            return new ResourcesDesc(null, new Locale[0], new String[0], new String[0]);
        }

        @Override
        public URL getCodeBase() {
            return CODEBASE_URL;
        }

        @Override
        public SecurityDesc getSecurity() {
            if (haveSecurity) {
                return new SecurityDesc(this, SecurityDesc.SANDBOX_PERMISSIONS, null);
            } else {
                return new SecurityDesc(null, SecurityDesc.SANDBOX_PERMISSIONS, null);
            }
        }
    };
    private static final String isWSA = "isWebstartApplication";

    static void setStaticField(Field field, Object newValue) throws Exception {
        field.setAccessible(true);
        field.set(null, newValue);
    }

    private void setWSA() throws Exception {
        setStaticField(JNLPRuntime.class.getDeclaredField(isWSA), true);
    }

    private void setApplet() throws Exception {
        setStaticField(JNLPRuntime.class.getDeclaredField(isWSA), false);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        setStaticField(JNLPRuntime.class.getDeclaredField(isWSA), false);


    }

    @Bug(id = {"PR895",
        "http://mail.openjdk.java.net/pipermail/distro-pkg-dev/2012-March/017626.html",
        "http://mail.openjdk.java.net/pipermail/distro-pkg-dev/2012-March/017667.html"})
    @Test
    @Remote
    public void testClassResourceLoadSuccessCachingApplication() throws Exception {
        setWSA();
        //we are testing new resource not in cache
        testResourceCaching("net/sourceforge/jnlp/about/Main.class");
    }

    @Test
    @Remote
    public void testClassResourceLoadSuccessCachingApplet() throws Exception {
        setApplet();
        //so new resource again not in cache
        testResourceCaching("net/sourceforge/jnlp/about/Main.class");
    }

    @Test
    @Remote
    public void testResourceLoadSuccessCachingApplication() throws Exception {
        setWSA();
        //we are testing new resource not in cache
        testResourceCaching("net/sourceforge/jnlp/about/resources/about.html");
    }

    @Test
    @Remote
    public void testResourceLoadSuccessCachingApplet() throws Exception {
        setApplet();
        //so new resource again not in cache
        testResourceCaching("net/sourceforge/jnlp/about/resources/about.html");
    }

    public void testResourceCaching(String r) throws Exception {
        testResourceCaching(r, true);
    }

    public void testResourceCaching(String r, boolean shouldExists) throws Exception {
        JNLPFile dummyJnlpFile = new DummyJNLPFile(true);

        JNLPClassLoader parent = new JNLPClassLoader(dummyJnlpFile, null);
        CodeBaseClassLoader classLoader = new CodeBaseClassLoader(new URL[]{JAR_URL, CODEBASE_URL}, parent);

        int level = 10;
        if (shouldExists) {
            //for found the "caching" is by internal logic.Always faster, but who knows how...
            //to keep the test stabile keep the difference minimal
            level = 1;
        }
        long startTime, stopTime;

        startTime = System.nanoTime();
        URL u1 = classLoader.findResource(r);
        if (shouldExists) {
            Assert.assertNotNull(u1);
        } else {
            Assert.assertNull(u1);
        }
        stopTime = System.nanoTime();
        long timeOnFirstTry = stopTime - startTime;
        ServerAccess.logErrorReprint("" + timeOnFirstTry);

        startTime = System.nanoTime();
        URL u2 = classLoader.findResource(r);
        if (shouldExists) {
            Assert.assertNotNull(u1);
        } else {
            Assert.assertNull(u2);
        }
        stopTime = System.nanoTime();
        long timeOnSecondTry = stopTime - startTime;
        ServerAccess.logErrorReprint("" + timeOnSecondTry);

        assertTrue(timeOnSecondTry < (timeOnFirstTry / level));
    }

    @Bug(id = {"PR895",
        "http://mail.openjdk.java.net/pipermail/distro-pkg-dev/2012-March/017626.html",
        "http://mail.openjdk.java.net/pipermail/distro-pkg-dev/2012-March/017667.html"})
    @Test
    @Remote
    public void testResourceLoadFailureCachingApplication() throws Exception {
        setWSA();
        testResourceCaching("net/sourceforge/jnlp/about/Main_FOO_.class", false);
    }

    @Test
    public void testResourceLoadFailureCachingApplet() throws Exception {
        setApplet();
        testResourceCaching("net/sourceforge/jnlp/about/Main_FOO_.class", false);
    }

    @Test
    @Remote
    public void testParentClassLoaderIsAskedForClassesApplication() throws Exception {
        setWSA();
        testParentClassLoaderIsAskedForClasses();
    }

    @Test
    @Remote
    public void testParentClassLoaderIsAskedForClassesApplet() throws Exception {
        setApplet();
        testParentClassLoaderIsAskedForClasses();
    }

    public void testParentClassLoaderIsAskedForClasses() throws Exception {
        JNLPFile dummyJnlpFile = new DummyJNLPFile(true);

        final boolean[] parentWasInvoked = new boolean[1];

        JNLPClassLoader parent = new JNLPClassLoader(dummyJnlpFile, null) {

            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                parentWasInvoked[0] = true;
                throw new ClassNotFoundException(name);
            }
        };
        CodeBaseClassLoader classLoader = new CodeBaseClassLoader(new URL[]{JAR_URL, CODEBASE_URL}, parent);
        try {
            classLoader.findClass("foo");
            assertFalse("should not happen", true);
        } catch (ClassNotFoundException cnfe) { /* ignore */ }

        assertTrue(parentWasInvoked[0]);
    }

    @Test
    public void testNullFileSecurityDescApplication() throws Exception {
        setWSA();
        testNullFileSecurityDesc();
    }

    @Test
    @Remote
    public void testNullFileSecurityDescApplet() throws Exception {
        setApplet();
        testNullFileSecurityDesc();
    }

    public void testNullFileSecurityDesc() throws Exception {
        JNLPFile dummyJnlpFile = new DummyJNLPFile(false);

        JNLPClassLoader parent = new JNLPClassLoader(dummyJnlpFile, null);
        CodeBaseClassLoader classLoader = new CodeBaseClassLoader(new URL[]{JAR_URL, CODEBASE_URL}, parent);

        Exception ex = null;
        try {
            classLoader.findClass("foo");
        } catch (Exception exx) {
            ex = exx;
            ServerAccess.logException(ex);
        }
        Assert.assertNotNull(ex);
        Assert.assertTrue(ex instanceof ClassNotFoundException);


        //search dor resources is not relvant to null jnlp file for applets
        ex = null;
        URL res = null;
        try {
            //not cached
            res = classLoader.findResource("net/sourceforge/jnlp/about/resources/notes.html");
        } catch (Exception exx) {
            ex = exx;
            ServerAccess.logException(ex);
        }
        if (JNLPRuntime.isWebstartApplication()) {
            Assert.assertNull(res);
            Assert.assertNotNull(ex);
            Assert.assertTrue(ex instanceof NullJnlpFileException);
        } else {
            Assert.assertNull(ex);
            Assert.assertNotNull(res);
        }

        ex = null;
        res = null;
        try {
            //now cached
            res = classLoader.findResource("net/sourceforge/jnlp/about/resources/notes.html");
        } catch (Exception exx) {
            ex = exx;
            ServerAccess.logException(ex);
        }
        if (JNLPRuntime.isWebstartApplication()) {
            Assert.assertNotNull(ex);
            Assert.assertTrue(ex instanceof NullJnlpFileException);
            Assert.assertNull(res);
        } else {
            Assert.assertNull(ex);
            Assert.assertNotNull(res);
        }
    }
}
