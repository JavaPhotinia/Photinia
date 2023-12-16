package utils;

import analysis.CreateEdge;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.IOUtils;
import soot.Scene;
import soot.SootClass;
import soot.SourceLocator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class FileUtil {

    public static void writeFile(String filePath, String filename, String sets) {
        FileWriter fw = null;
        PrintWriter out = null;
        try {
            fw = new FileWriter(filePath + filename, true);
            out = new PrintWriter(fw);
            out.write(sets);
            out.println();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                assert fw != null;
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            assert out != null;
            out.close();
        }
    }

    public static Set<String> getBeanXmlPaths(String keyname) {
        Set<String> res = new HashSet<>();
        String property = ConfigUtil.getProperties().getProperty(keyname);
        if (property != null && !property.equals("\"\"")) {
            String[] split = property.split(";");
            Collections.addAll(res, split);
        }
        return res;
    }

    public static String getConfigString(String keyname) {
        return ConfigUtil.getProperties().getProperty(keyname);
    }

    public static Set<File> findSpecialFiles(File sourceFile, String fileType) {
        Set<File> specialFiles = new HashSet<>();
        if (sourceFile.isDirectory()) {
            File[] files = sourceFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    specialFiles.addAll(findSpecialFiles(file, fileType));
                }
            }
        } else {
            if (sourceFile.getName().endsWith(fileType)) {
                specialFiles.add(sourceFile);
            } else if (sourceFile.getName().endsWith(".jar")) {
                try {
                    JarFile jarFile = new JarFile(sourceFile);
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry jarEntry = entries.nextElement();
                        if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(fileType)) {
                            specialFiles.add(new File(jarEntry.getName()));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return specialFiles;
    }

    public static void preprocessLib(String libClassPaths) {
        for (String libClassPath : libClassPaths.split(":")) {
            preprocessSingleLib(libClassPath);
        }
    }

    public static void preprocessLib(List<String> libClassPaths) {
        for (String libClassPath : libClassPaths) {
            preprocessSingleLib(libClassPath);
        }
    }

    public static void preprocessSingleLib(String libClassPath) {
        if (includeSpecialLib(libClassPath)) {
            System.out.println("Add Source body: " + libClassPath);
            for (String clzName : SourceLocator.v().getClassesUnder(libClassPath)) {
                Scene.v().tryLoadClass(clzName, SootClass.BODIES).setLibraryClass();
                FileUtil.parseLibConfig(libClassPath);
            }
        }
    }


    private static void parseLibConfig(String libClassPath) {
        try {
            URL url = new URL("jar:file:" + libClassPath + "!/META-INF");
            JarFile jarFile = ((JarURLConnection) url.openConnection()).getJarFile();
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                if (!jarEntry.isDirectory() && jarEntry.getName().endsWith(".json")) {
                    Object parseObj = JSONObject.parse(IOUtils.toString(jarFile.getInputStream(jarEntry)));
                    if (parseObj instanceof JSONObject) {
                        JSONObject jsonObject = (JSONObject) parseObj;
                        if (jsonObject.get("groups") != null) {
                            getSpecialProperty(jsonObject, "groups");
                        }
                        if (jsonObject.get("properties") != null) {
                            getSpecialProperty(jsonObject, "properties");
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("not find META-INFO:" + libClassPath);
        }
    }

    private static void getSpecialProperty(JSONObject jsonObject, String propertyName) {
        for (Object o : (JSONArray) jsonObject.get(propertyName)) {
            JSONObject jsonObj = (JSONObject) o;
            if (jsonObj.getString("type") != null && jsonObj.getString("type").equals("java.lang.Boolean")
                    && jsonObj.getBoolean("defaultValue") != null && !jsonObj.getBoolean("defaultValue")) {
                CreateEdge.disableProperty.add(jsonObj.getString("name"));
            }
        }
    }

    private static boolean includeSpecialLib(String libPath) {
        String includeJarList = getConfigString("include_jar");
        for (String includeJar : includeJarList.split(";")) {
            if (libPath.contains(includeJar)) {
                return true;
            }
        }
        return false;
    }
}
