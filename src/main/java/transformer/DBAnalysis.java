package transformer;

import bean.DBMethodBean;
import frontend.AnnotationAnalysis;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;
import soot.util.Chain;
import utils.DBUtil;
import utils.FileUtil;
import utils.SootUtil;
import utils.XMLReadUtil;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStreamReader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DBAnalysis {
    private final DBUtil dbUtil = new DBUtil();
    private static final Map<SootClass, Document> mapXMLMap = findXML();

    public Set<DBMethodBean> findDBMethods(Chain<SootClass> applicationClasses, Set<SootClass> DBSootClasses) {
        Set<DBMethodBean> res = new HashSet<>();
        Set<SootClass> mappers = filterInterface(applicationClasses);
        mappers.addAll(DBSootClasses);
        Set<SootClass> otherClasses = getConcreteClass(applicationClasses, mappers);
        for (SootClass mapper : mappers) {
            if (mapXMLMap.containsKey(mapper)) {
                dbUtil.resolveMybatisXml(mapXMLMap.get(mapper));
            }
            res.addAll(resolveDBFromAnnoAndXML(mapper));
        }

        for (SootClass otherClass : otherClasses) {
            // res.addAll(resolveDBFromClass(otherClass));
        }
        return res;
    }

    public Set<DBMethodBean> resolveDBFromAnnoAndXML(SootClass mapper) {
        Set<DBMethodBean> res = new HashSet<>();
        List<SootMethod> methods = new ArrayList<>(mapper.getMethods());
        Map<String, String> resultMap = new HashMap<>();
        if (DBUtil.mapperAndResultMap.containsKey(mapper.getName())) {
            resultMap = DBUtil.mapperAndResultMap.get(mapper.getName());
        }
        methods.addAll(findAllMethodInInterface(mapper));
        for (SootMethod method : methods) {
            DBMethodBean dbMethodBean = new DBMethodBean();
            dbMethodBean.setMethodName(method.getSignature());
            dbMethodBean.setSootMethod(method);
            dbMethodBean.setSootClass(mapper);
            dbMethodBean.setParamCount(method.getParameterCount());
            dbUtil.fillDBMethod(method, dbMethodBean);
            if (dbMethodBean.getMethodType() == null || dbMethodBean.getMethodContext() == null) {
                String key = mapper.getName() + "." + method.getName();
                if (DBUtil.mapperCategory.containsKey(key)) {
                    dbMethodBean.setMethodType(DBUtil.mapperCategory.get(key));
                    dbMethodBean.setMethodContext(DBUtil.mapperAndSQL.get(key));
                }
                if (DBUtil.xmlReturnClass.containsKey(key)) {
                    dbMethodBean.setResultClass(DBUtil.xmlReturnClass.get(key));
                }
            }
            if (dbMethodBean.getMethodContext() != null && !dbMethodBean.getMethodContext().equals("")) {
                dbUtil.solve(dbMethodBean, resultMap);
            } else {
                dbUtil.solveForMethodName(dbMethodBean);
            }

            res.add(dbMethodBean);
        }
        return res;
    }

    // public Set<DBMethodBean> resolveDBFromClass(SootClass otherClass) {
    //     Set<DBMethodBean> res = new HashSet<>();
    //     for (SootMethod method : otherClass.getMethods()) {
    //         if (method.getName().equals("<init>") || method.isAbstract()) {
    //             continue;
    //         }
    //         Body body = method.retrieveActiveBody();
    //         Collection<Unit> units = body.getUnits();
    //         List<Unit> unitList = new ArrayList<>(units);
    //         for (Unit unit : unitList) {
    //             Stmt stmt = (Stmt) unit;
    //             String type = isContainsHibernateMethod(stmt.toString());
    //             if (!type.equals("")) {
    //                 DBMethodBean dbMethodBean = new DBMethodBean();
    //                 dbMethodBean.setMethodType(type);
    //                 dbMethodBean.setMethodContext("");
    //                 dbMethodBean.setMethodName(method.getSignature());
    //                 dbMethodBean.setSootMethod(method);
    //                 dbMethodBean.setSootClass(otherClass);
    //                 dbMethodBean.setParamCount(method.getParameterCount());
    //                 List<String> paramNames = body.getParameterLocals().stream().map(Object::toString).collect(Collectors.toList());
    //                 dbMethodBean.setParamNames(paramNames);
    //                 List<String> paramTypes = method.getParameterTypes().stream().map(Type::toString).collect(Collectors.toList());
    //                 res.add(dbMethodBean);
    //                 break;
    //             }
    //         }
    //     }
    //     return res;
    // }

    private List<SootMethod> findAllMethodInInterface(SootClass mapper) {
        List<SootMethod> methods = new ArrayList<>();
        for (SootClass anInterface : mapper.getInterfaces()) {
            methods.addAll(anInterface.getMethods());
            methods.addAll(findAllMethodInInterface(anInterface));
        }
        return methods;
    }

    private Set<SootClass> getConcreteClass(Chain<SootClass> applicationClasses, Set<SootClass> mappers) {
        Set<SootClass> otherClasses = new HashSet<>(applicationClasses);
        otherClasses.removeAll(mappers);
        otherClasses.removeIf(other -> other.getName().contains("org.springframework"));
        return otherClasses;
    }

    public static Map<SootClass, Document> findXML() {
        Map<SootClass, Document> mapXMLMap = new HashMap<>();
        List<String> sourcePaths = Options.v().process_dir();
        Set<File> xmlFiles = new HashSet<>();
        for (String sourcePath : sourcePaths) {
            xmlFiles.addAll(FileUtil.findSpecialFiles(new File(sourcePath), "xml"));
        }
        for (File xmlFile : xmlFiles) {
            Document document = XMLReadUtil.parseXML(xmlFile);
            if (document != null) {
                SootClass mapper = XMLReadUtil.findMapper(document);
                if (mapper != null) {
                    mapXMLMap.put(mapper, document);
                }
            }
        }
        return mapXMLMap;
    }

    public static Map<SootClass, Document> findXMLFromJarfindXMLFromJar() {
        Map<SootClass, Document> mapXMLMap = new HashMap<>();
        // List<String> sourcePaths = CreateEdge.dataBaseDir;
        List<String> sourcePaths = new ArrayList<>();
        for (String sourcePath : sourcePaths) {
            try {
                JarFile jarFile = new JarFile(sourcePath);
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String path = entry.getName();
                    if (path.endsWith(".xml")) {
                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                        documentBuilderFactory.setValidating(false);
                        documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
                        InputSource is = new InputSource(new InputStreamReader(jarFile.getInputStream(entry)));
                        Document document = documentBuilder.parse(is);
                        if (document != null) {
                            SootClass mapper = XMLReadUtil.findMapper(document);
                            if (mapper != null) {
                                mapXMLMap.put(mapper, document);
                            }
                        }
                    }
                }
            } catch (Exception ignore) {

            }
        }
        return mapXMLMap;
    }

    public Set<SootClass> filterInterface(Chain<SootClass> applicationClasses) {
        Collection<SootClass> elementsUnsorted = applicationClasses.getElementsUnsorted();
        Iterator<SootClass> iteratorMapper = elementsUnsorted.iterator();
        Set<SootClass> databaseModels = new HashSet<>();
        while (iteratorMapper.hasNext()) {
            SootClass sootClass = iteratorMapper.next();
            if (!sootClass.isInterface() || SootUtil.getSubClassOrImplementClass(sootClass).size() > 0 || sootClass.isPhantom()) {
                continue;
            }
            boolean impl = false;
            for (String mapperPackage : AnnotationAnalysis.mapperPackages) {
                if (sootClass.getPackageName().startsWith(mapperPackage)) {
                    databaseModels.add(sootClass);
                    impl = true;
                    break;
                }
            }
            if (!impl) {
                for (SootClass anInterface : sootClass.getInterfaces()) {
                    if ((anInterface.getPackageName().startsWith("org.springframework.data")
                            || anInterface.getName().equals("com.baomidou.mybatisplus.core.mapper.BaseMapper"))
                            && !sootClass.getPackageName().startsWith("org.springframework")) {
                        databaseModels.add(sootClass);
                        impl = true;
                        break;
                    }
                }
            }
            if (!impl) {
                if (!sootClass.getPackageName().startsWith("org.springframework")
                        && (sootClass.getName().toLowerCase().endsWith("dao")
                        || sootClass.getName().toLowerCase().endsWith("mapper")
                        || sootClass.getName().toLowerCase().endsWith("repository"))) {
                    databaseModels.add(sootClass);
                }
            }
        }
        return databaseModels;
    }

    private String isContainsHibernateMethod(String stmt) {
        if (stmt.contains("interfaceinvoke")) {
            if (stmt.contains("org.hibernate.query.Query")) {
                return "select";
            } else if (stmt.contains("org.hibernate.Session")) {
                if (stmt.contains("save")) {
                    return "insert";
                } else if (stmt.contains("update")) {
                    return "update";
                } else if (stmt.contains("delete")) {
                    return "delete";
                } else if (stmt.contains("saveOrUpdate")) {
                    return "update";
                } else if (stmt.contains("get")) {
                    return "select";
                } else if (stmt.contains("load")) {
                    return "select";
                } else if (stmt.contains("createQuery")) {
                    return "select";
                }
            }
        }
        return "";
    }
}
