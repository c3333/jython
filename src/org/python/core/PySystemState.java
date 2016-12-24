// Copyright (c) Corporation for National Research Initiatives
package org.python.core;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.AccessControlException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import jnr.posix.util.Platform;
import com.carrotsearch.sizeof.RamUsageEstimator;

import org.python.Version;
import org.python.core.adapter.ClassicPyObjectAdapter;
import org.python.core.adapter.ExtensiblePyObjectAdapter;
import org.python.core.packagecache.PackageManager;
import org.python.core.packagecache.SysPackageManager;
import org.python.expose.ExposedGet;
import org.python.expose.ExposedType;
import org.python.modules.Setup;
import org.python.util.Generic;

/**
 * The "sys" module.
 */
// xxx Many have lamented, this should really be a module!
// but it will require some refactoring to see this wish come true.
public class PySystemState extends PyObject implements AutoCloseable,
        ClassDictInit, Closeable, Traverseproc {

    public static final String PYTHON_CACHEDIR = "python.cachedir";
    public static final String PYTHON_CACHEDIR_SKIP = "python.cachedir.skip";
    public static final String PYTHON_CONSOLE_ENCODING = "python.console.encoding";
    public static final String PYTHON_IO_ENCODING = "python.io.encoding";
    public static final String PYTHON_IO_ERRORS = "python.io.errors";
    protected static final String CACHEDIR_DEFAULT_NAME = "cachedir";

    public static final String JYTHON_JAR = "jython.jar";
    public static final String JYTHON_DEV_JAR = "jython-dev.jar";

    public static final PyString version = new PyString(Version.getVersion());

    public static final PyTuple subversion = new PyTuple(new PyString("Jython"), Py.newString(""),
            Py.newString(""));

    public static final int hexversion = ((Version.PY_MAJOR_VERSION << 24)
            | (Version.PY_MINOR_VERSION << 16) | (Version.PY_MICRO_VERSION << 8)
            | (Version.PY_RELEASE_LEVEL << 4) | (Version.PY_RELEASE_SERIAL << 0));

    public static final PyVersionInfo version_info = getVersionInfo();

    public static final int maxunicode = 1114111;

    // XXX: we should someday make this Long.MAX_VALUE, but see test_index.py
    // for tests that would need to pass but today would not.
    public final static int maxsize = Integer.MAX_VALUE;

    public final static PyString float_repr_style = Py.newString("short");

    public static boolean py3kwarning = false;

    public final static Class flags = Options.class;

    public final static PyTuple _mercurial = new PyTuple(
            Py.newString("Jython"),
            Py.newString(Version.getHGIdentifier()),
            Py.newString(Version.getHGVersion()));
    /**
     * The copyright notice for this release.
     */

    public static final PyObject copyright = Py.newString(
            "Copyright (c) 2000-2016 Jython Developers.\n" + "All rights reserved.\n\n" +
            "Copyright (c) 2000 BeOpen.com.\n" + "All Rights Reserved.\n\n" +
            "Copyright (c) 2000 The Apache Software Foundation.\n" + "All rights reserved.\n\n" +
            "Copyright (c) 1995-2000 Corporation for National Research Initiatives.\n"
                + "All Rights Reserved.\n\n" +
            "Copyright (c) 1991-1995 Stichting Mathematisch Centrum, Amsterdam.\n"
                + "All Rights Reserved.");

    private static Map<String, String> builtinNames;
    public static PyTuple builtin_module_names = null;

    public static PackageManager packageManager;
    private static File cachedir;

    private static PyList defaultPath;
    private static PyList defaultArgv;
    private static PyObject defaultExecutable;

    public static Properties registry; // = init_registry();
    public static PyObject prefix;
    public static PyObject exec_prefix = Py.EmptyString;

    public static final PyString byteorder = new PyString("big");
    public static final int maxint = Integer.MAX_VALUE;
    public static final int minint = Integer.MIN_VALUE;

    private static boolean initialized = false;

    /** The arguments passed to this program on the command line. */
    public PyList argv = new PyList();

    public PyObject modules;
    public Map<String, PyModule> modules_reloading;
    private ReentrantLock importLock;
    private ClassLoader syspathJavaLoader;
    public PyList path;

    public PyList warnoptions = new PyList();
    public PyObject builtins;
    private static PyObject defaultPlatform = new PyString("java");
    public PyObject platform = defaultPlatform;

    public PyList meta_path;
    public PyList path_hooks;
    public PyObject path_importer_cache;

    // Only defined if interactive, see https://docs.python.org/2/library/sys.html#sys.ps1
    public PyObject ps1 = PyAttributeDeleted.INSTANCE;
    public PyObject ps2 = PyAttributeDeleted.INSTANCE;

    public PyObject executable;

    private String currentWorkingDir;

    private ClassLoader classLoader = null;

    public PyObject stdout, stderr, stdin;
    public PyObject __stdout__, __stderr__, __stdin__;

    public PyObject __displayhook__, __excepthook__;

    public PyObject last_value = Py.None;
    public PyObject last_type = Py.None;
    public PyObject last_traceback = Py.None;

    public PyObject __name__ = new PyString("sys");

    public PyObject __dict__;

    private int recursionlimit = 1000;

    private int checkinterval = 100;

    private codecs.CodecState codecState;

    /** true when a SystemRestart is triggered. */
    public boolean _systemRestart = false;

    /** Whether bytecode should be written to disk on import. */
    public boolean dont_write_bytecode = false;

    // Automatically close resources associated with a PySystemState when they get GCed
    private final PySystemStateCloser closer;
    private static final ReferenceQueue<PySystemState> systemStateQueue =
            new ReferenceQueue<PySystemState>();
    private static final ConcurrentMap<WeakReference<PySystemState>, PySystemStateCloser> sysClosers =
            Generic.concurrentMap();

    // float_info
    public static final PyObject float_info = FloatInfo.getInfo();

    // long_info
    public static final PyObject long_info = LongInfo.getInfo();

    public PySystemState() {
        initialize();
        closer = new PySystemStateCloser(this);
        modules = new PyStringMap();
        modules_reloading = new HashMap<String, PyModule>();
        importLock = new ReentrantLock();
        syspathJavaLoader = new SyspathJavaLoader(imp.getParentClassLoader());

        argv = (PyList)defaultArgv.repeat(1);
        path = (PyList)defaultPath.repeat(1);
        path.append(Py.newString(JavaImporter.JAVA_IMPORT_PATH_ENTRY));
        path.append(Py.newString(ClasspathPyImporter.PYCLASSPATH_PREFIX));
        executable = defaultExecutable;
        builtins = getDefaultBuiltins();
        platform = defaultPlatform;

        meta_path = new PyList();
        path_hooks = new PyList();
        path_hooks.append(new JavaImporter());
        path_hooks.append(org.python.modules.zipimport.zipimporter.TYPE);
        path_hooks.append(ClasspathPyImporter.TYPE);
        path_importer_cache = new PyDictionary();

        currentWorkingDir = new File("").getAbsolutePath();

        dont_write_bytecode = Options.dont_write_bytecode;
        py3kwarning = Options.py3k_warning;
        // Set up the initial standard ins and outs
        String mode = Options.unbuffered ? "b" : "";
        int buffering = Options.unbuffered ? 0 : 1;
        stdin = __stdin__ = new PyFile(System.in, "<stdin>", "r" + mode, buffering, false);
        stdout = __stdout__ = new PyFile(System.out, "<stdout>", "w" + mode, buffering, false);
        stderr = __stderr__ = new PyFile(System.err, "<stderr>", "w" + mode, 0, false);
        initEncoding();

        __displayhook__ = new PySystemStateFunctions("displayhook", 10, 1, 1);
        __excepthook__ = new PySystemStateFunctions("excepthook", 30, 3, 3);

        if (builtins == null) {
            builtins = getDefaultBuiltins();
        }
        modules.__setitem__("__builtin__", new PyModule("__builtin__", getDefaultBuiltins()));
        __dict__ = new PyStringMap();
        __dict__.invoke("update", getType().fastGetDict());
        __dict__.__setitem__("displayhook", __displayhook__);
        __dict__.__setitem__("excepthook", __excepthook__);

    }

    public static void classDictInit(PyObject dict) {
        // XXX: Remove bean accessors for settrace/profile that we don't want
        dict.__setitem__("trace", null);
        dict.__setitem__("profile", null);
    }

    void reload() throws PyIgnoreMethodTag {
        __dict__.invoke("update", getType().fastGetDict());
    }

    private static void checkReadOnly(String name) {
        if (name == "__dict__" || name == "__class__" || name == "registry"
                || name == "exec_prefix" || name == "packageManager") {
            throw Py.TypeError("readonly attribute");
        }
    }

    private static void checkMustExist(String name) {
        if (name == "__dict__" || name == "__class__" || name == "registry"
                || name == "exec_prefix" || name == "platform" || name == "packageManager"
                || name == "builtins" || name == "warnoptions") {
            throw Py.TypeError("readonly attribute");
        }
    }

    /**
     * Initialise the encoding of <code>sys.stdin</code>, <code>sys.stdout</code>, and
     * <code>sys.stderr</code>, and their error handling policy, from registry variables.
     * Under the console app util.jython, values reflect PYTHONIOENCODING if not overridden.
     * Note that the encoding must name a Python codec, as in <code>codecs.encode()</code>.
     */
    private void initEncoding() {
        // Two registry variables, counterparts to PYTHONIOENCODING = [encoding][:errors]
        String encoding = registry.getProperty(PYTHON_IO_ENCODING);
        String errors = registry.getProperty(PYTHON_IO_ERRORS);

        if (encoding==null) {
            // We still don't have an explicit selection for this: match the console.
            encoding = Py.getConsole().getEncoding();
        }

        ((PyFile)stdin).setEncoding(encoding, errors);
        ((PyFile)stdout).setEncoding(encoding, errors);
        ((PyFile)stderr).setEncoding(encoding, "backslashreplace");
    }

    @Deprecated
    public void shadow() {
        // Now a no-op
    }

    private static class DefaultBuiltinsHolder {

        static final PyObject builtins = fillin();

        static PyObject fillin() {
            PyObject temp = new PyStringMap();
            __builtin__.fillWithBuiltins(temp);
            return temp;
        }
    }

    public static PyObject getDefaultBuiltins() {
        return DefaultBuiltinsHolder.builtins;
    }

    public PyObject getBuiltins() {
        return builtins;
    }

    public void setBuiltins(PyObject value) {
        builtins = value;
        modules.__setitem__("__builtin__", new PyModule("__builtin__", value));
    }

    public PyObject getWarnoptions() {
        return warnoptions;
    }

    public void setWarnoptions(PyObject value) {
        warnoptions = new PyList(value);
    }

    public PyObject getPlatform() {
        return platform;
    }

    public void setPlatform(PyObject value) {
        platform = value;
    }

    public synchronized codecs.CodecState getCodecState() {
        if (codecState == null) {
            codecState = new codecs.CodecState();
            try {
                imp.load("encodings");
            } catch (PyException exc) {
                if (exc.type != Py.ImportError) {
                    throw exc;
                }
            }
        }
        return codecState;
    }

    public ReentrantLock getImportLock() {
        return importLock;
    }

    public ClassLoader getSyspathJavaLoader() {
        return syspathJavaLoader;
    }

    // xxx fix this accessors
    @Override
    public PyObject __findattr_ex__(String name) {
        if (name == "exc_value") {
            PyException exc = Py.getThreadState().exception;
            if (exc == null) {
                return null;
            }
            return exc.value;
        } else if (name == "exc_type") {
            PyException exc = Py.getThreadState().exception;
            if (exc == null) {
                return null;
            }
            return exc.type;
        } else if (name == "exc_traceback") {
            PyException exc = Py.getThreadState().exception;
            if (exc == null) {
                return null;
            }
            return exc.traceback;
        } else {
            PyObject ret = super.__findattr_ex__(name);
            if (ret != null) {
                if (ret instanceof PyMethod) {
                    if (__dict__.__finditem__(name) instanceof PyReflectedFunction) {
                        return ret; // xxx depends on nonstandard __dict__
                    }
                } else if (ret == PyAttributeDeleted.INSTANCE) {
                    return null;
                } else {
                    return ret;
                }
            }

            return __dict__.__finditem__(name);
        }
    }

    @Override
    public void __setattr__(String name, PyObject value) {
        checkReadOnly(name);
        if (name == "builtins") {
            setBuiltins(value);
        } else {
            PyObject ret = getType().lookup(name); // xxx fix fix fix
            if (ret != null && ret._doset(this, value)) {
                return;
            }
            __dict__.__setitem__(name, value);
        }
    }

    @Override
    public void __delattr__(String name) {
        checkMustExist(name);
        PyObject ret = getType().lookup(name); // xxx fix fix fix
        if (ret != null) {
            ret._doset(this, PyAttributeDeleted.INSTANCE);
        }
        try {
            __dict__.__delitem__(name);
        } catch (PyException pye) { // KeyError
            if (ret == null) {
                throw Py.AttributeError(name);
            }
        }
    }

    // xxx
    @Override
    public void __rawdir__(PyDictionary accum) {
        accum.update(__dict__);
    }

    @Override
    public String toString() {
        return "<module '" + __name__ + "' (built-in)>";
    }

    public int getrecursionlimit() {
        return recursionlimit;
    }

    @SuppressWarnings("unused")
    public long getsizeof(Object obj, long defaultVal) {
        return getsizeof(obj);
    }

    public long getsizeof(Object obj) {
        return RamUsageEstimator.shallowSizeOf(obj);
    }

    public void setrecursionlimit(int recursionlimit) {
        if (recursionlimit <= 0) {
            throw Py.ValueError("Recursion limit must be positive");
        }
        this.recursionlimit = recursionlimit;
    }

    public PyObject gettrace() {
        ThreadState ts = Py.getThreadState();
        if (ts.tracefunc == null) {
            return Py.None;
        } else {
            return ((PythonTraceFunction)ts.tracefunc).tracefunc;
        }
    }

    public void settrace(PyObject tracefunc) {
        ThreadState ts = Py.getThreadState();
        if (tracefunc == Py.None) {
            ts.tracefunc = null;
        } else {
            ts.tracefunc = new PythonTraceFunction(tracefunc);
        }
    }

    public PyObject getprofile() {
        ThreadState ts = Py.getThreadState();
        if (ts.profilefunc == null) {
            return Py.None;
        } else {
            return ((PythonTraceFunction)ts.profilefunc).tracefunc;
        }
    }

    public void setprofile(PyObject profilefunc) {
        ThreadState ts = Py.getThreadState();
        if (profilefunc == Py.None) {
            ts.profilefunc = null;
        } else {
            ts.profilefunc = new PythonTraceFunction(profilefunc);
        }
    }

    public PyString getdefaultencoding() {
        return new PyString(codecs.getDefaultEncoding());
    }

    public void setdefaultencoding(String encoding) {
        codecs.setDefaultEncoding(encoding);
    }

    public PyObject getfilesystemencoding() {
        return Py.None;
    }


    /* get and setcheckinterval really do nothing, but it helps when some code tries to use these */
    public PyInteger getcheckinterval() { return new PyInteger(checkinterval); }

    public void setcheckinterval(int interval) { checkinterval = interval; }

    /**
     * Change the current working directory to the specified path.
     *
     * path is assumed to be absolute and canonical (via os.path.realpath).
     *
     * @param path a path String
     */
    public void setCurrentWorkingDir(String path) {
        currentWorkingDir = path;
    }

    /**
     * Return a string representing the current working directory.
     *
     * @return a path String
     */
    public String getCurrentWorkingDir() {
        return currentWorkingDir;
    }

    /**
     * Resolve a path. Returns the full path taking the current working directory into account.
     *
     * @param path a path String
     * @return a resolved path String
     */
    public String getPath(String path) {
        return getPath(this, path);
    }

    /**
     * Resolve a path. Returns the full path taking the current working directory into account.
     *
     * Like getPath but called statically. The current PySystemState is only consulted for the
     * current working directory when it's necessary (when the path is relative).
     *
     * @param path a path String
     * @return a resolved path String
     */
    public static String getPathLazy(String path) {
        // XXX: This method likely an unnecessary optimization
        return getPath(null, path);
    }

    private static String getPath(PySystemState sys, String path) {
        if (path != null) {
            path = getFile(sys, path).getAbsolutePath();
        }
        return path;
    }

    /**
     * Resolve a path, returning a {@link File}, taking the current working directory into account.
     *
     * @param path a path <code>String</code>
     * @return a resolved <code>File</code>
     */
    public File getFile(String path) {
        return getFile(this, path);
    }

    /**
     * Resolve a path, returning a {@link File}, taking the current working directory of the
     * specified <code>PySystemState</code> into account. Use of a <code>static</code> here is a
     * trick to avoid getting the current state if the path is absolute. (Noted that this may be
     * needless optimisation.)
     *
     * @param sys a <code>PySystemState</code> or null meaning the current one
     * @param path a path <code>String</code>
     * @return a resolved <code>File</code>
     */
    private static File getFile(PySystemState sys, String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            // path meaning depends on the current working directory
            if (sys == null) {
                sys = Py.getSystemState();
            }
            String cwd = sys.getCurrentWorkingDir();
            if (Platform.IS_WINDOWS) {
                // Form absolute reference (with mysterious Windows semantics)
                file = getWindowsFile(cwd, path);
            } else {
                // Form absolute reference (with single slash)
                file = new File(cwd, path);
            }
        }
        return file;
    }

    /**
     * Resolve a relative path against the supplied current working directory or Windows environment
     * working directory for any drive specified in the path. and return a file object. Essentially
     * equivalent to os.path.join, but the work is done by {@link File}. The intention is that
     * calling {@link File#getAbsolutePath()} should return the corresponding absolute path.
     * <p>
     * Note: in the context where we use this method, <code>path</code> is already known not to be
     * absolute, and <code>cwd</code> is assumed to be absolute.
     *
     * @param cwd current working directory (of some {@link PySystemState})
     * @param path to resolve
     * @return specifier of the intended file
     */
    private static File getWindowsFile(String cwd, String path) {
        // Assumptions: cwd is absolute and path is not absolute

        // Start by getting the slashes the right (wrong) way round.
        if (path.indexOf('/') >= 0) {
            path = path.replace('/', '\\');
        }

        // Does path start with a drive letter?
        char d = driveLetter(path);
        if (d != 0) {
            if (d == driveLetter(cwd)) {
                /*
                 * path specifies the same drive letter as in the cwd of this PySystemState. Let
                 * File interpret the rest of the path relative to cwd as parent.
                 */
                return new File(cwd, path.substring(2));
            } else {
                // Let File resolve the specified drive against the process environment.
                return new File(path);
            }

        } else if (path.startsWith("\\")) {
            // path replaces the file part of the cwd. (Method assumes path is not UNC.)
            if (driveLetter(cwd) != 0) {
                // cwd has a drive letter
                return new File(cwd.substring(0, 2), path);
            } else {
                // cwd has no drive letter, so should be a UNC path \\host\share\directory\etc
                return new File(uncShare(cwd), path);
            }

        } else {
            // path is relative to the cwd of this PySystemState.
            return new File(cwd, path);
        }
    }

    /**
     * Return the Windows drive letter from the start of the path, upper case, or 0 if
     * the path does not start X: where X is a letter.
     *
     * @param path to examine
     * @return drive letter or char 0 if no drive letter
     */
    private static char driveLetter(String path) {
        if (path.length() >= 2 && path.charAt(1) == ':') {
            // Looks like both strings start with a drive letter
            char pathDrive = path.charAt(0);
            if (Character.isLetter(pathDrive)) {
                return Character.toUpperCase(pathDrive);
            }
        }
        return (char)0;
    }

    /**
     * Return the Windows UNC share name from the start of the path, or <code>null</code> if the
     * path is not of Windows UNC type. The path has to be formed with Windows-backslashes:
     * slashes '/' are not accepted as a substitute here.
     *
     * @param path to examine
     * @return share name or null
     */
    private static String uncShare(String path) {
        int n = path.length();
        // Has to accommodate at least \\A (3 chars)
        if (n >= 5 && path.startsWith("\\\\")) {
            // Look for the separator backslash A\B
            int p = path.indexOf('\\', 2);
            // Has to be at least index 3 (path begins \\A) and 2 more characters left \B
            if (p >= 3 && n > p + 2) {
                // Look for directory backslash that ends the share name
                int dir = path.indexOf('\\', p + 1);
                if (dir < 0) {
                    // path has the form \\A\B (is just the share name)
                    return path;
                } else if (dir > p + 1) {
                    // path has the form \\A\B\C
                    return path.substring(0, dir);
                }
            }
        }
        return null;
    }

    public void callExitFunc() throws PyIgnoreMethodTag {
        PyObject exitfunc = __findattr__("exitfunc");
        if (exitfunc != null) {
            try {
                exitfunc.__call__();
            } catch (PyException exc) {
                if (!exc.match(Py.SystemExit)) {
                    Py.println(stderr, Py.newString("Error in sys.exitfunc:"));
                }
                Py.printException(exc);
            }
        }
        Py.flushLine();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    private static String findRoot(Properties preProperties, Properties postProperties,
            String jarFileName) {
        String root = null;
        try {
            if (postProperties != null) {
                root = postProperties.getProperty("python.home");
            }
            if (root == null) {
                root = preProperties.getProperty("python.home");
            }
            if (root == null) {
                root = preProperties.getProperty("install.root");
            }
            determinePlatform(preProperties);
        } catch (Exception exc) {
            return null;
        }
        // If install.root is undefined find JYTHON_JAR in class.path
        if (root == null || root.equals("")) {
            String classpath = preProperties.getProperty("java.class.path");
            if (classpath != null) {
                String lowerCaseClasspath = classpath.toLowerCase();
                int jarIndex = lowerCaseClasspath.indexOf(JYTHON_JAR);
                if (jarIndex < 0) {
                    jarIndex = lowerCaseClasspath.indexOf(JYTHON_DEV_JAR);
                }
                if (jarIndex >= 0) {
                    int start = classpath.lastIndexOf(File.pathSeparator, jarIndex) + 1;
                    root = classpath.substring(start, jarIndex);
                } else if (jarFileName != null) {
                    // in case JYTHON_JAR is referenced from a MANIFEST inside another jar on the
                    // classpath
                    root = new File(jarFileName).getParent();
                }
            }
        }
        if (root == null) {
            return null;
        }
        File rootFile = new File(root);
        try {
            return rootFile.getCanonicalPath();
        } catch (IOException ioe) {
            return rootFile.getAbsolutePath();
        }
    }

    private static void determinePlatform(Properties props) {
        String version = props.getProperty("java.version");
        if (version == null) {
            version = "???";
        }
        String lversion = version.toLowerCase();
        if (lversion.startsWith("java")) {
            version = version.substring(4, version.length());
        }
        if (lversion.startsWith("jdk") || lversion.startsWith("jre")) {
            version = version.substring(3, version.length());
        }
        if (version.equals("12")) {
            version = "1.2";
        }
        defaultPlatform = new PyString("java" + version);
    }

    private static void initRegistry(Properties preProperties, Properties postProperties,
            boolean standalone, String jarFileName) {
        if (registry != null) {
            Py.writeError("systemState", "trying to reinitialize registry");
            return;
        }

        registry = preProperties;
        String prefix = findRoot(preProperties, postProperties, jarFileName);
        String exec_prefix = prefix;

        // Load the default registry
        if (prefix != null) {
            if (prefix.length() == 0) {
                prefix = exec_prefix = ".";
            }
            try {
                // user registry has precedence over installed registry
                File homeFile = new File(registry.getProperty("user.home"), ".jython");
                addRegistryFile(homeFile);
                addRegistryFile(new File(prefix, "registry"));
            } catch (Exception exc) {
                // Continue
            }
        }
        if (prefix != null) {
            PySystemState.prefix = Py.newString(prefix);
        }
        if (exec_prefix != null) {
            PySystemState.exec_prefix = Py.newString(exec_prefix);
        }
        try {
            String jythonpath = System.getenv("JYTHONPATH");
            if (jythonpath != null) {
                registry.setProperty("python.path", jythonpath);
            }
        } catch (SecurityException e) {
            // Continue
        }

        // Now the post properties (possibly set by custom JythonInitializer).
        registry.putAll(postProperties);
        if (standalone) {
            // set default standalone property (if not yet set)
            if (!registry.containsKey(PYTHON_CACHEDIR_SKIP)) {
                registry.put(PYTHON_CACHEDIR_SKIP, "true");
            }
        }

        /*
         *  The console encoding is the one used by line-editing consoles to decode on the OS side and
         *  encode on the Python side. It must be a Java codec name, so any relationship to
         *  python.io.encoding is dubious.
         */
        if (!registry.containsKey(PYTHON_CONSOLE_ENCODING)) {
            String encoding = getPlatformEncoding();
            if (encoding != null) {
                registry.put(PYTHON_CONSOLE_ENCODING, encoding);
            }
        }

        // Set up options from registry
        Options.setFromRegistry();
    }

    /**
     * Return the encoding of the underlying platform, if we can work it out by any means at all.
     *
     * @return the encoding of the underlying platform
     */
    private static String getPlatformEncoding() {
        // first try to grab the Console encoding
        String encoding = getConsoleEncoding();
        if (encoding == null) {
            try {
                // Not quite the console encoding (differs on Windows)
                encoding = System.getProperty("file.encoding");
            } catch (SecurityException se) {
                // ignore, can't do anything about it
            }
        }
        return encoding;
    }

    /**
     * @return the console encoding; can be <code>null</code>
     */
    private static String getConsoleEncoding() {
        String encoding = null;
        try {
            Method encodingMethod = java.io.Console.class.getDeclaredMethod("encoding");
            encodingMethod.setAccessible(true); // private static method
            encoding = (String)encodingMethod.invoke(Console.class);
        } catch (Exception e) {
            // ignore any exception
        }
        return encoding;
    }

    /**
     * Merge the contents of a property file into the registry without overriding any values already
     * set there.
     *
     * @param file
     */
    private static void addRegistryFile(File file) {
        if (file.exists()) {
            if (!file.isDirectory()) {
                // pre (e.g. system) properties should override the registry,
                // therefore only add missing properties from this registry file
                Properties fileProperties = new Properties();
                try {
                    FileInputStream fp = new FileInputStream(file);
                    try {
                        fileProperties.load(fp);
                        for (Entry kv : fileProperties.entrySet()) {
                            Object key = kv.getKey();
                            if (!registry.containsKey(key)) {
                                registry.put(key, kv.getValue());
                            }
                        }
                    } finally {
                        fp.close();
                    }
                } catch (IOException e) {
                    System.err.println("couldn't open registry file: " + file.toString());
                }
            } else {
                System.err.println("warning: " + file.toString() + " is a directory, not a file");
            }
        }
    }

    public static Properties getBaseProperties() {
        try {
            return System.getProperties();
        } catch (AccessControlException ace) {
            return new Properties();
        }
    }

    public static synchronized void initialize() {
        initialize(null, null);
    }

    public static synchronized void initialize(Properties preProperties, Properties postProperties) {
        initialize(preProperties, postProperties, new String[] {""});
    }

    public static synchronized void initialize(Properties preProperties, Properties postProperties,
            String[] argv) {
        initialize(preProperties, postProperties, argv, null);
    }

    public static synchronized void initialize(Properties preProperties, Properties postProperties,
            String[] argv, ClassLoader classLoader) {
        initialize(preProperties, postProperties, argv, classLoader, new ClassicPyObjectAdapter());
    }

    public static synchronized void initialize(Properties preProperties, Properties postProperties,
            String[] argv, ClassLoader classLoader, ExtensiblePyObjectAdapter adapter) {
        if (initialized) {
            return;
        }
        if (preProperties == null) {
            preProperties = getBaseProperties();
        }
        if (postProperties == null) {
            postProperties = new Properties();
        }
        try {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context != null) {
                if (initialize(preProperties, postProperties, argv, classLoader, adapter, context)) {
                    return;
                }
            } else {
                Py.writeDebug("initializer", "Context class loader null, skipping");
            }
            ClassLoader sysStateLoader = PySystemState.class.getClassLoader();
            if (sysStateLoader != null) {
                if (initialize(preProperties, postProperties, argv, classLoader, adapter,
                        sysStateLoader)) {
                    return;
                }
            } else {
                Py.writeDebug("initializer", "PySystemState.class class loader null, skipping");
            }
        } catch (UnsupportedCharsetException e) {
            Py.writeWarning("initializer",
                    "Unable to load the UTF-8 charset to read an initializer definition");
            e.printStackTrace(System.err);
        } catch (SecurityException e) {
            // Must be running in a security environment that doesn't allow access to the class
            // loader
        } catch (Exception e) {
            Py.writeWarning("initializer",
                    "Unexpected exception thrown while trying to use initializer service");
            e.printStackTrace(System.err);
        }
        doInitialize(preProperties, postProperties, argv, classLoader, adapter);
    }

    private static final String INITIALIZER_SERVICE =
            "META-INF/services/org.python.core.JythonInitializer";

    /**
     * Attempts to read a SystemStateInitializer service from the given class loader, instantiate
     * it, and initialize with it.
     *
     * @throws UnsupportedCharsetException if unable to load UTF-8 to read a service definition
     * @return true if a service is found and successfully initializes.
     */
    private static boolean initialize(Properties pre, Properties post, String[] argv,
            ClassLoader sysClassLoader, ExtensiblePyObjectAdapter adapter,
            ClassLoader initializerClassLoader) {
        InputStream in = initializerClassLoader.getResourceAsStream(INITIALIZER_SERVICE);
        if (in == null) {
            Py.writeDebug("initializer", "'" + INITIALIZER_SERVICE + "' not found on "
                    + initializerClassLoader);
            return false;
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
        String className;
        try {
            className = r.readLine();
        } catch (IOException e) {
            Py.writeWarning("initializer", "Failed reading '" + INITIALIZER_SERVICE + "' from "
                    + initializerClassLoader);
            e.printStackTrace(System.err);
            return false;
        }
        Class<?> initializer;
        try {
            initializer = initializerClassLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            Py.writeWarning("initializer", "Specified initializer class '" + className
                    + "' not found, continuing");
            return false;
        }
        try {
            ((JythonInitializer)initializer.newInstance()).initialize(pre, post, argv,
                    sysClassLoader, adapter);
        } catch (Exception e) {
            Py.writeWarning("initializer", "Failed initializing with class '" + className
                    + "', continuing");
            e.printStackTrace(System.err);
            return false;
        }
        if (!initialized) {
            Py.writeWarning("initializer", "Initializer '" + className
                    + "' failed to call doInitialize, using default initialization");
        }
        return initialized;
    }

    public static synchronized PySystemState doInitialize(Properties preProperties,
            Properties postProperties, String[] argv, ClassLoader classLoader,
            ExtensiblePyObjectAdapter adapter) {
        if (initialized) {
            return Py.defaultSystemState;
        }
        initialized = true;
        Py.setAdapter(adapter);
        boolean standalone = false;
        String jarFileName = Py._getJarFileName();
        if (jarFileName != null) {
            standalone = isStandalone(jarFileName);
        }

        // initialize the Jython registry
        initRegistry(preProperties, postProperties, standalone, jarFileName);

        // other initializations
        initBuiltins(registry);
//        initStaticFields();

        // Initialize the path (and add system defaults)
        defaultPath = initPath(registry, standalone, jarFileName);
        defaultArgv = initArgv(argv);
        defaultExecutable = initExecutable(registry);

        // Set up the known Java packages
        initPackages(registry);

        // Condition the console
        initConsole(registry);

        // Finish up standard Python initialization...
        Py.defaultSystemState = new PySystemState();
        Py.setSystemState(Py.defaultSystemState);
        if (classLoader != null) {
            Py.defaultSystemState.setClassLoader(classLoader);
        }
        Py.initClassExceptions(getDefaultBuiltins());

        // Make sure that Exception classes have been loaded
        new PySyntaxError("", 1, 1, "", "");

        // Cause sys to export the console handler that was installed
        Py.defaultSystemState.__setattr__("_jy_console", Py.java2py(Py.getConsole()));

        return Py.defaultSystemState;
    }

    private static PyVersionInfo getVersionInfo() {
        String s;
        if (Version.PY_RELEASE_LEVEL == 0x0A) {
            s = "alpha";
        } else if (Version.PY_RELEASE_LEVEL == 0x0B) {
            s = "beta";
        } else if (Version.PY_RELEASE_LEVEL == 0x0C) {
            s = "candidate";
        } else if (Version.PY_RELEASE_LEVEL == 0x0F) {
            s = "final";
        } else if (Version.PY_RELEASE_LEVEL == 0xAA) {
            s = "snapshot";
        } else {
            throw new RuntimeException("Illegal value for PY_RELEASE_LEVEL: "
                    + Version.PY_RELEASE_LEVEL);
        }
        return new PyVersionInfo(
                Py.newInteger(Version.PY_MAJOR_VERSION),
                Py.newInteger(Version.PY_MINOR_VERSION),
                Py.newInteger(Version.PY_MICRO_VERSION),
                Py.newString(s),
                Py.newInteger(Version.PY_RELEASE_SERIAL));
    }

    public static boolean isPackageCacheEnabled() {
        return cachedir != null;
    }

    private static void initCacheDirectory(Properties props) {
        String skip = props.getProperty(PYTHON_CACHEDIR_SKIP, "false");
        if (skip.equalsIgnoreCase("true")) {
            cachedir = null;
            return;
        }
        cachedir = new File(props.getProperty(PYTHON_CACHEDIR, CACHEDIR_DEFAULT_NAME));
        if (!cachedir.isAbsolute()) {
            cachedir = new File(prefix == null ? null : prefix.toString(), cachedir.getPath());
        }
    }

    private static void initPackages(Properties props) {
        initCacheDirectory(props);
        File pkgdir;
        if (cachedir != null) {
            pkgdir = new File(cachedir, "packages");
        } else {
            pkgdir = null;
        }
        packageManager = new SysPackageManager(pkgdir, props);
    }

    private static PyList initArgv(String[] args) {
        PyList argv = new PyList();
        if (args != null) {
            for (String arg : args) {
                argv.append(Py.newStringOrUnicode(arg));
            }
        }
        return argv;
    }

    /**
     * Determine the default sys.executable value from the registry.
     * If registry is not set (as in standalone jython jar), will use sys.prefix + /bin/jython(.exe) and the file may
     * not exist. Users can create a wrapper in it's place to make it work in embedded environments.
     * Only if sys.prefix is null, returns Py.None
     *
     * @param props a Properties registry
     * @return a PyObject path string or Py.None
     */
    private static PyObject initExecutable(Properties props) {
        String executable = props.getProperty("python.executable");
        if (executable == null) {
            if (prefix == null) {
                return Py.None;
            } else {
                executable = prefix.asString() + File.separator + "bin" + File.separator;
                if (Platform.IS_WINDOWS) {
                    executable += "jython.exe";
                } else {
                    executable += "jython";
                }
            }
        }

        File executableFile = new File(executable);
        try {
            executableFile = executableFile.getCanonicalFile();
        } catch (IOException ioe) {
            executableFile = executableFile.getAbsoluteFile();
        }
        return new PyString(executableFile.getPath());
    }

    /**
     * Wrap standard input with a customised console handler specified in the property
     * <code>python.console</code> in the supplied property set, which in practice is the
     * fully-initialised Jython {@link #registry}. The value of <code>python.console</code> is the
     * name of a class that implements {@link org.python.core.Console}. An instance is constructed
     * with the value of <code>python.console.encoding</code>, and the console
     * <code>System.in</code> returns characters in that encoding. After the call, the console
     * object may be accessed via {@link Py#getConsole()}.
     *
     * @param props containing (or not) <code>python.console</code>
     */
    private static void initConsole(Properties props) {
        // At this stage python.console.encoding is always defined (but null=default)
        String encoding = props.getProperty(PYTHON_CONSOLE_ENCODING);
        // The console type is chosen by this registry entry:
        String consoleName = props.getProperty("python.console", "").trim();
        // And must be of type ...
        final Class<Console> consoleType = Console.class;

        if (consoleName.length() > 0 && Py.isInteractive()) {
            try {
                // Load the class specified as the console
                Class<?> consoleClass = Class.forName(consoleName);

                // Ensure it can be cast to the interface type of all consoles
                if (!consoleType.isAssignableFrom(consoleClass)) {
                    throw new ClassCastException();
                }

                // Construct an instance
                Constructor<?> consoleConstructor = consoleClass.getConstructor(String.class);
                Object consoleObject = consoleConstructor.newInstance(encoding);
                Console console = consoleType.cast(consoleObject);

                // Replace System.in with stream this console manufactures
                Py.installConsole(console);
                return;

            } catch (NoClassDefFoundError e) {
                writeConsoleWarning(consoleName, "not found");
            } catch (ClassCastException e) {
                writeConsoleWarning(consoleName, "does not implement " + consoleType);
            } catch (NoSuchMethodException e) {
                writeConsoleWarning(consoleName, "has no constructor from String");
            } catch (InvocationTargetException e) {
                writeConsoleWarning(consoleName, e.getCause().toString());
            } catch (Exception e) {
                writeConsoleWarning(consoleName, e.toString());
            }
        }

        // No special console required, or requested installation failed somehow
        try {
            // Default is a plain console
            Py.installConsole(new PlainConsole(encoding));
            return;
        } catch (Exception e) {
            /*
             * May end up here if prior console won't uninstall: but then at least we have a
             * console. Or it may be an unsupported encoding, in which case Py.getConsole() will try
             * "ascii"
             */
            writeConsoleWarning(consoleName, e.toString());
        }
    }

    /**
     * Convenience method wrapping {@link Py#writeWarning(String, String)} to issue a warning
     * message something like:
     * "console: Failed to load 'org.python.util.ReadlineConsole': <b>msg</b>.". It's only a warning
     * because the interpreter will fall back to a plain console, but it is useful to know exactly
     * why it didn't work.
     *
     * @param consoleName console class name we're trying to initialise
     * @param msg specific cause of the failure
     */
    private static void writeConsoleWarning(String consoleName, String msg) {
        Py.writeWarning("console", "Failed to install '" + consoleName + "': " + msg + ".");
    }

    private static void addBuiltin(String name) {
        String classname;
        String modname;

        int colon = name.indexOf(':');
        if (colon != -1) {
            // name:fqclassname
            modname = name.substring(0, colon).trim();
            classname = name.substring(colon + 1, name.length()).trim();
            if (classname.equals("null")) {
                // name:null, i.e. remove it
                classname = null;
            }
        } else {
            modname = name.trim();
            classname = "org.python.modules." + modname;
        }
        if (classname != null) {
            builtinNames.put(modname, classname);
        } else {
            builtinNames.remove(modname);
        }
    }

    private static void initBuiltins(Properties props) {
        builtinNames = Generic.map();

        // add the oddball builtins that are specially handled
        builtinNames.put("__builtin__", "");
        builtinNames.put("sys", "");

        // add builtins specified in the Setup.java file
        for (String builtinModule : Setup.builtinModules) {
            addBuiltin(builtinModule);
        }

        // add builtins specified in the registry file
        String builtinprop = props.getProperty("python.modules.builtin", "");
        StringTokenizer tok = new StringTokenizer(builtinprop, ",");
        while (tok.hasMoreTokens()) {
            addBuiltin(tok.nextToken());
        }

        int n = builtinNames.size();
        PyObject[] built_mod = new PyObject[n];
        int i = 0;
        for (String key : builtinNames.keySet()) {
            built_mod[i++] = Py.newString(key);
        }
        builtin_module_names = new PyTuple(built_mod);
    }

    public static String getBuiltin(String name) {
        return builtinNames.get(name);
    }

    private static PyList initPath(Properties props, boolean standalone, String jarFileName) {
        PyList path = new PyList();
        addPaths(path, props.getProperty("python.path", ""));
        if (prefix != null) {
            String libpath = new File(prefix.toString(), "Lib").toString();
            path.append(new PyString(libpath));
        }
        if (standalone) {
            // standalone jython: add the /Lib directory inside JYTHON_JAR to the path
            addPaths(path, jarFileName + "/Lib");
        }

        return path;
    }

    /**
     * Check if we are in standalone mode.
     *
     * @param jarFileName The name of the jar file
     *
     * @return <code>true</code> if we have a standalone .jar file, <code>false</code> otherwise.
     */
    private static boolean isStandalone(String jarFileName) {
        boolean standalone = false;
        if (jarFileName != null) {
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(jarFileName);
                JarEntry jarEntry = jarFile.getJarEntry("Lib/os.py");
                standalone = jarEntry != null;
            } catch (IOException ioe) {
                // Continue
            } finally {
                if (jarFile != null) {
                    try {
                        jarFile.close();
                    } catch (IOException e) {
                        // Continue
                    }
                }
            }
        }
        return standalone;
    }

    private static void addPaths(PyList path, String pypath) {
        StringTokenizer tok = new StringTokenizer(pypath, java.io.File.pathSeparator);
        while (tok.hasMoreTokens()) {
            path.append(new PyString(tok.nextToken().trim()));
        }
    }

    public static PyJavaPackage add_package(String n) {
        return add_package(n, null);
    }

    public static PyJavaPackage add_package(String n, String contents) {
        return packageManager.makeJavaPackage(n, contents, null);
    }

    /**
     * Add a classpath directory to the list of places that are searched for java packages.
     * <p>
     * <b>Note</b>. Classes found in directory and sub-directory are not made available to jython by
     * this call. It only makes the java package found in the directory available. This call is
     * mostly useful if jython is embedded in an application that deals with its own class loaders.
     * A servlet container is a very good example. Calling add_classdir("<context>/WEB-INF/classes")
     * makes the java packages in WEB-INF classes available to jython import. However the actual
     * classloading is completely handled by the servlet container's context classloader.
     */
    public static void add_classdir(String directoryPath) {
        packageManager.addDirectory(new File(directoryPath));
    }

    /**
     * Add a .jar & .zip directory to the list of places that are searched for java .jar and .zip
     * files. The .jar and .zip files found will not be cached.
     * <p>
     * <b>Note</b>. Classes in .jar and .zip files found in the directory are not made available to
     * jython by this call. See the note for add_classdir(dir) for more details.
     *
     * @param directoryPath The name of a directory.
     *
     * @see #add_classdir
     */
    public static void add_extdir(String directoryPath) {
        packageManager.addJarDir(directoryPath, false);
    }

    /**
     * Add a .jar & .zip directory to the list of places that are searched for java .jar and .zip
     * files.
     * <p>
     * <b>Note</b>. Classes in .jar and .zip files found in the directory are not made available to
     * jython by this call. See the note for add_classdir(dir) for more details.
     *
     * @param directoryPath The name of a directory.
     * @param cache Controls if the packages in the zip and jar file should be cached.
     *
     * @see #add_classdir
     */
    public static void add_extdir(String directoryPath, boolean cache) {
        packageManager.addJarDir(directoryPath, cache);
    }

    // Not public by design. We can't rebind the displayhook if
    // a reflected function is inserted in the class dict.

    static void displayhook(PyObject o) {
        /* Print value except if None */
        /* After printing, also assign to '_' */
        /* Before, set '_' to None to avoid recursion */
        if (o == Py.None) {
            return;
        }

        PyObject currentBuiltins = Py.getSystemState().getBuiltins();
        currentBuiltins.__setitem__("_", Py.None);
        Py.stdout.println(o.__repr__());
        currentBuiltins.__setitem__("_", o);
    }

    static void excepthook(PyObject type, PyObject val, PyObject tb) {
        Py.displayException(type, val, tb, null);
    }

    /**
     * Exit a Python program with the given status.
     *
     * @param status the value to exit with
     * @exception Py.SystemExit always throws this exception. When caught at top level the program
     *                will exit.
     */
    public static void exit(PyObject status) {
        throw new PyException(Py.SystemExit, status);
    }

    /**
     * Exit a Python program with the status 0.
     */
    public static void exit() {
        exit(Py.None);
    }

    public static PyTuple exc_info() {
        PyException exc = Py.getThreadState().exception;
        if (exc == null) {
            return new PyTuple(Py.None, Py.None, Py.None);
        }
        PyObject tb = exc.traceback;
        PyObject value = exc.value;
        return new PyTuple(exc.type, value == null ? Py.None : value, tb == null ? Py.None : tb);
    }

    public static void exc_clear() {
        Py.getThreadState().exception = null;
    }

    public static PyFrame _getframe() {
        return _getframe(-1);
    }

    public static PyFrame _getframe(int depth) {
        PyFrame f = Py.getFrame();

        while (depth > 0 && f != null) {
            f = f.f_back;
            --depth;
        }
        if (f == null) {
            throw Py.ValueError("call stack is not deep enough");
        }
        return f;
    }

    public void registerCloser(Callable<Void> resourceCloser) {
        closer.registerCloser(resourceCloser);
    }

    public boolean unregisterCloser(Callable<Void> resourceCloser) {
        return closer.unregisterCloser(resourceCloser);
    }

    public void cleanup() {
        closer.cleanup();
    }

    public void close() { cleanup(); }

    public static class PySystemStateCloser {

        private final Set<Callable<Void>> resourceClosers = Collections.synchronizedSet(new LinkedHashSet<Callable<Void>>());
        private volatile boolean isCleanup = false;
        private final Thread shutdownHook;

        private PySystemStateCloser(PySystemState sys) {
            shutdownHook = initShutdownCloser();
            WeakReference<PySystemState> ref =
                    new WeakReference<PySystemState>(sys, systemStateQueue);
            sysClosers.put(ref, this);
            cleanupOtherClosers();
        }

        private static void cleanupOtherClosers() {
            Reference<? extends PySystemState> ref;
            while ((ref = systemStateQueue.poll()) != null) {
                PySystemStateCloser closer = sysClosers.get(ref);
                closer.cleanup();
            }
        }

        private void registerCloser(Callable<Void> closer) {
            if (!isCleanup) {
                resourceClosers.add(closer);
            }
        }

        private boolean unregisterCloser(Callable<Void> closer) {
            return resourceClosers.remove(closer);
        }

        private synchronized void cleanup() {
            if (isCleanup) {
                return;
            }
            isCleanup = true;

            // close this thread so we can unload any associated classloaders in cycle
            // with this instance
            if (shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException e) {
                    // JVM is already shutting down, so we cannot remove this shutdown hook anyway
                }
            }

            // Close the listed resources (and clear the list)
            runClosers();
            resourceClosers.clear();

            // XXX Not sure this is ok, but it makes repeat testing possible.
            // Re-enable the management of resource closers
            isCleanup = false;
        }
        private synchronized void runClosers() {
            // resourceClosers can be null in some strange cases
            if (resourceClosers != null) {
                /*
                    * Although a Set, the container iterates in the order closers were added. Make a Deque
                    * of it and deal from the top.
                    */
                LinkedList<Callable<Void>> rc = new LinkedList<Callable<Void>>(resourceClosers);
                Iterator<Callable<Void>> iter = rc.descendingIterator();

                while (iter.hasNext()) {
                    Callable<Void> callable = iter.next();
                    try {
                        callable.call();
                    } catch (Exception e) {
                        // just continue, nothing we can do
                    }
                }
            }
        }

        // Python scripts expect that files are closed upon an orderly cleanup of the VM.
        private Thread initShutdownCloser() {
            try {
                Thread shutdownHook = new Thread(new ShutdownCloser(this), "Jython Shutdown Closer");
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                return shutdownHook;
            } catch (SecurityException se) {
                Py.writeDebug("PySystemState", "Can't register cleanup closer hook");
                return null;
            }
        }

        private class ShutdownCloser implements Runnable {
            PySystemStateCloser closer = null;

            public ShutdownCloser(PySystemStateCloser closer) {
                super();
                this.closer = closer;
            }

            @Override
            public void run() {
                synchronized (this.closer) {
                    runClosers();
                    resourceClosers.clear();
                }
            }
        }

    }


    /* Traverseproc implementation */
    @Override
    public int traverse(Visitproc visit, Object arg) {
        int retVal;
        if (argv != null) {
            retVal = visit.visit(argv, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (modules != null) {
            retVal = visit.visit(modules, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (path != null) {
            retVal = visit.visit(path, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (warnoptions != null) {
            retVal = visit.visit(warnoptions, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (builtins != null) {
            retVal = visit.visit(builtins, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (platform != null) {
            retVal = visit.visit(platform, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (meta_path != null) {
            retVal = visit.visit(meta_path, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (path_hooks != null) {
            retVal = visit.visit(path_hooks, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (path_importer_cache != null) {
            retVal = visit.visit(path_importer_cache, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (ps1 != null) {
            retVal = visit.visit(ps1, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (ps2 != null) {
            retVal = visit.visit(ps2, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (executable != null) {
            retVal = visit.visit(executable, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (stdout != null) {
            retVal = visit.visit(stdout, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (stderr != null) {
            retVal = visit.visit(stderr, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (stdin != null) {
            retVal = visit.visit(stdin, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (__stdout__ != null) {
            retVal = visit.visit(__stdout__, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (__stderr__ != null) {
            retVal = visit.visit(__stderr__, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (__stdin__ != null) {
            retVal = visit.visit(__stdin__, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (__displayhook__ != null) {
            retVal = visit.visit(__displayhook__, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (__excepthook__ != null) {
            retVal = visit.visit(__excepthook__, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (last_value != null) {
            retVal = visit.visit(last_value, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (last_type != null) {
            retVal = visit.visit(last_type, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (last_traceback != null) {
            retVal = visit.visit(last_traceback, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (__name__ != null) {
            retVal = visit.visit(__name__, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        return __dict__ == null ? 0 : visit.visit(__dict__, arg);
    }

    @Override
    public boolean refersDirectlyTo(PyObject ob) {
        return ob != null && (ob == argv || ob ==  modules || ob == path
            || ob == warnoptions || ob == builtins || ob == platform
            || ob == meta_path || ob == path_hooks || ob == path_importer_cache
            || ob == ps1 || ob == ps2 || ob == executable || ob == stdout
            || ob == stderr || ob == stdin || ob == __stdout__ || ob == __stderr__
            || ob == __stdin__ || ob == __displayhook__ || ob == __excepthook__
            || ob ==  last_value || ob == last_type || ob == last_traceback
            || ob ==__name__ || ob == __dict__);
    }


    /**
     * Helper abstracting common code from {@link ShutdownCloser#run()} and
     * {@link PySystemStateCloser#cleanup()} to close resources (such as still-open files). The
     * closing sequence is from last-created resource to first-created, so that dependencies between
     * them are respected. (There are some amongst layers in the _io module.)
     *
     * @param resourceClosers to be called in turn
     */
}


@Untraversable
class PySystemStateFunctions extends PyBuiltinFunctionSet {

    PySystemStateFunctions(String name, int index, int minargs, int maxargs) {
        super(name, index, minargs, maxargs);
    }

    @Override
    public PyObject __call__(PyObject arg) {
        switch (index) {
            case 10:
                PySystemState.displayhook(arg);
                return Py.None;
            default:
                throw info.unexpectedCall(1, false);
        }
    }

    @Override
    public PyObject __call__(PyObject arg1, PyObject arg2, PyObject arg3) {
        switch (index) {
            case 30:
                PySystemState.excepthook(arg1, arg2, arg3);
                return Py.None;
            default:
                throw info.unexpectedCall(3, false);
        }
    }
}


/**
 * Value of a class or instance variable when the corresponding attribute is deleted. Used only in
 * PySystemState for now.
 */
@Untraversable
class PyAttributeDeleted extends PyObject {

    final static PyAttributeDeleted INSTANCE = new PyAttributeDeleted();

    private PyAttributeDeleted() {}

    @Override
    public String toString() {
        return "";
    }

    @Override
    public Object __tojava__(Class c) {
        if (c == PyObject.class) {
            return this;
        }
        // we can't quite "delete" non-PyObject attributes; settle for
        // null or nothing
        if (c.isPrimitive()) {
            return Py.NoConversion;
        }
        return null;
    }
}


@ExposedType(name = "sys.float_info", isBaseType = false)
class FloatInfo extends PyTuple {

    @ExposedGet
    public PyObject max, max_exp, max_10_exp, min, min_exp, min_10_exp, dig, mant_dig, epsilon,
            radix, rounds;

    public static final PyType TYPE = PyType.fromClass(FloatInfo.class);

    private FloatInfo(PyObject... vals) {
        super(TYPE, vals);

        max = vals[0];
        max_exp = vals[1];
        max_10_exp = vals[2];
        min = vals[3];
        min_exp = vals[4];
        min_10_exp = vals[5];
        dig = vals[6];
        mant_dig = vals[7];
        epsilon = vals[8];
        radix = vals[9];
        rounds = vals[10];
    }

    static public FloatInfo getInfo() {
        // max_10_exp, dig and epsilon taken from ssj library Num class
        // min_10_exp, mant_dig, radix and rounds by ɲeuroburɳ (bit.ly/Iwo2LT)
        return new FloatInfo( //
                Py.newFloat(Double.MAX_VALUE),       // DBL_MAX
                Py.newLong(Double.MAX_EXPONENT),     // DBL_MAX_EXP
                Py.newLong(308),                     // DBL_MIN_10_EXP
                Py.newFloat(Double.MIN_VALUE),       // DBL_MIN
                Py.newLong(Double.MIN_EXPONENT),     // DBL_MIN_EXP
                Py.newLong(-307),                    // DBL_MIN_10_EXP
                Py.newLong(10),                      // DBL_DIG
                Py.newLong(53),                      // DBL_MANT_DIG
                Py.newFloat(2.2204460492503131e-16), // DBL_EPSILON
                Py.newLong(2),                       // FLT_RADIX
                Py.newLong(1)                        // FLT_ROUNDS
        );
    }


    /* Traverseproc implementation */
    @Override
    public int traverse(Visitproc visit, Object arg) {
        int retVal = super.traverse(visit, arg);
        if (max != null) {
            retVal = visit.visit(max, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (max_exp != null) {
            retVal = visit.visit(max_exp, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (max_10_exp != null) {
            retVal = visit.visit(max_10_exp, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (min != null) {
            retVal = visit.visit(min, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (min_exp != null) {
            retVal = visit.visit(min_exp, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (min_10_exp != null) {
            retVal = visit.visit(min_10_exp, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (dig != null) {
            retVal = visit.visit(dig, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (mant_dig != null) {
            retVal = visit.visit(mant_dig, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (epsilon != null) {
            retVal = visit.visit(epsilon, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        if (radix != null) {
            retVal = visit.visit(radix, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        return rounds == null ? 0 : visit.visit(rounds, arg);
    }

    @Override
    public boolean refersDirectlyTo(PyObject ob) {
        return ob != null && (ob == max || ob == max_exp || ob == max_10_exp || ob == min
            || ob == min_exp || ob == min_10_exp || ob == dig
            || ob == mant_dig || ob == epsilon || ob == radix || ob == rounds);
    }
}


@ExposedType(name = "sys.long_info", isBaseType = false)
class LongInfo extends PyTuple {

    @ExposedGet
    public PyObject bits_per_digit, sizeof_digit;

    public static final PyType TYPE = PyType.fromClass(LongInfo.class);

    private LongInfo(PyObject... vals) {
        super(TYPE, vals);

        bits_per_digit = vals[0];
        sizeof_digit = vals[1];
    }

    // XXX: I've cheated and just used the values that CPython gives me for my
    // local Ubuntu system. I'm not sure that they are correct.
    static public LongInfo getInfo() {
        return new LongInfo(Py.newLong(30), Py.newLong(4));
    }


    /* Traverseproc implementation */
    @Override
    public int traverse(Visitproc visit, Object arg) {
        int retVal = super.traverse(visit, arg);
        if (bits_per_digit != null) {
            retVal = visit.visit(bits_per_digit, arg);
            if (retVal != 0) {
                return retVal;
            }
        }
        return sizeof_digit == null ? 0 : visit.visit(sizeof_digit, arg);
    }

    @Override
    public boolean refersDirectlyTo(PyObject ob) {
        return ob != null && (ob == bits_per_digit || ob == sizeof_digit);
    }
}
