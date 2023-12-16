package analysis;

import backend.FrameworkModelingEngine;
import backend.GenerateSyntheticClass;
import backend.GenerateSyntheticClassImpl;
import bean.*;
import frontend.*;
import soot.*;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JReturnVoidStmt;
import soot.tagkit.AnnotationTag;
import soot.util.Chain;
import transformer.BasicCoRTransformer;
import transformer.DBAnalysis;
import transformer.HandlerModelEnum;
import transformer.SpringAnnotationTag;
import utils.*;

import java.util.*;
import java.util.stream.Collectors;

import static frontend.AnnotationAnalysis.removeConfigurationsMethod;
import static utils.StringConstantUtil.*;

public class CreateEdge {
    private final AnnotationAnalysis annotationAnalysis = new AnnotationAnalysis();
    public static final Map<SootClass, SootMethod> beansAndMethods = new HashMap<>();
    public static final Set<SootClass> allBeansAndInterfaces = new HashSet<>();
    public static final Set<SootClass> singletonComponents = new HashSet<>();
    public static final Set<SootClass> prototypeComponents = new HashSet<>();
    public static final Set<SootClass> allComponents = new HashSet<>();
    public static final Map<String, SootClass> interfaceToBeans = new HashMap<>();
    public static final Set<SootClass> prototypeInterfaces = new HashSet<>();
    private final Set<SootClass> DBSootClasses = new HashSet<>();
    public static final Set<String> needInitBeans = new HashSet<>();

    public static Map<String, List<ConstructorArgBean>> collect = null;
    public static Chain<SootClass> sootAppClassChain = Scene.v().getApplicationClasses();
    public static Chain<SootClass> sootLibClassChain = Scene.v().getLibraryClasses();
    private final JimpleUtil jimpleUtil = new JimpleUtil();
    protected String dummyClassName = "synthetic.method.dummyMainClass";
    public static SootMethod projectMainMethod;
    public static Set<String> disableProperty = new HashSet<>();
    private final Set<SootClass> hasBeenProcess = new HashSet<>();
    // public static List<String> dataBaseDir = new ArrayList<>();

    public void initCallGraph() {
        // dataBaseDir = inputs;
        scanAllBeans();
        DAOPatternAnalysis();
        generateEntryPoints();
        initBean();
        CoRPatternAnalysis();
        dependencyInjectAnalysis();
        invokeController();
    }

    private void scanAllBeans() {
        Set<String> bean_xml_paths = FileUtil.getBeanXmlPaths("bean_xml_paths");
        if (bean_xml_paths.size() > 0) {
            for (XmlBeanClazz xmlBeanSootClazz : getXMLBeanSootClazzes(bean_xml_paths)) {
                for (SootClass anInterface : xmlBeanSootClazz.getSootClass().getInterfaces()) {
                    interfaceToBeans.put(anInterface.getName(), xmlBeanSootClazz.getSootClass());
                }
                allComponents.add(xmlBeanSootClazz.getSootClass());
                allBeansAndInterfaces.add(xmlBeanSootClazz.getSootClass());
                hasBeenProcess.add(xmlBeanSootClazz.getSootClass());
                interfaceToBeans.put(xmlBeanSootClazz.getSootClass().getName(), xmlBeanSootClazz.getSootClass());
                if (xmlBeanSootClazz.getScope().equals("singleton")) {
                    singletonComponents.add(xmlBeanSootClazz.getSootClass());
                } else if (xmlBeanSootClazz.getScope().equals("prototype")) {
                    prototypeComponents.add(xmlBeanSootClazz.getSootClass());
                }
            }
            XMLDocumentHolder xmlHolder = getXMLHolder(bean_xml_paths);
            if (xmlHolder != null) {
                List<ConstructorArgBean> argConstructors = xmlHolder.getArgConstructors();
                collect = argConstructors.stream().collect(Collectors.groupingBy(ConstructorArgBean::getClazzName, Collectors.toList()));
            }
        }
        scanAllBean(sootAppClassChain.getElementsUnsorted());
        scanAllBean(sootLibClassChain.getElementsUnsorted());
    }

    private void scanAllBean(Collection<SootClass> sootClassCollection) {
        for (SootClass sootClass : sootClassCollection) {
            if (hasBeenProcess.contains(sootClass) || sootClass.resolvingLevel() == SootClass.DANGLING || sootClass.isJavaLibraryClass()
                    || AnnotationAnalysis.removeConfigurations.contains(sootClass) || sootClass.getMethodCount() < 1
                    || sootClass instanceof SootModuleInfo || isSkip(sootClass)) {
                continue;
            }
            hasBeenProcess.add(sootClass);
            List<SootClass> subclasses = SootUtil.getSubClassOrImplementClass(sootClass);
            if (subclasses.size() > 0) {
                scanAllBean(subclasses);
            }
            if (scanInnerBean(sootClass)) continue;
            int annotationTag = annotationAnalysis.getAllComponents(sootClass, null, sootClass);
            if (AnnotationAnalysis.removeConfigurations.contains(sootClass)) {
                continue;
            } else if (annotationTag == 0) {
                for (SootClass superClassesAndInterface : SootUtil.getSuperClassesAndInterfaces(sootClass)) {
                    annotationTag = annotationAnalysis.getAllComponents(superClassesAndInterface, null, sootClass);
                    if (annotationTag != 0) {
                        break;
                    }
                }
            }
            if (!AnnotationAnalysis.removeConfigurations.contains(sootClass) && SpringAnnotationTag.isBean(annotationTag)) {
                allComponents.add(sootClass);
                allBeansAndInterfaces.add(sootClass);
                if (SpringAnnotationTag.isMapper(annotationTag)) {
                    DBSootClasses.add(sootClass);
                }
                findAllSuperclass(sootClass);
                interfaceToBeans.put(sootClass.getName(), sootClass);
                if (SpringAnnotationTag.isPrototype(annotationTag)) {
                    prototypeComponents.add(sootClass);
                } else {
                    singletonComponents.add(sootClass);
                }
                annotationAnalysis.getAllBeans(sootClass);
            } else {
                if (SpringAnnotationTag.isPrototype(annotationTag)) {
                    prototypeInterfaces.add(sootClass);
                    allComponents.add(sootClass);
                }
            }
        }
    }

    private boolean scanInnerBean(SootClass sootClass) {
        if (sootClass.isInnerClass()) {
            SootClass outerClass = sootClass.getOuterClass();
            if (SootUtil.classAndInnerClass.containsKey(outerClass)) {
                SootUtil.classAndInnerClass.get(outerClass).add(sootClass);
            } else {
                Set<SootClass> innerClass = new HashSet<>();
                innerClass.add(sootClass);
                SootUtil.classAndInnerClass.put(outerClass, innerClass);
            }
            if (!hasBeenProcess.contains(outerClass)) {
                scanAllBean(Collections.singleton(outerClass));
            }
            if (AnnotationAnalysis.removeConfigurations.contains(outerClass)) {
                AnnotationAnalysis.removeConfigurations.add(sootClass);
                return true;
            }
        }
        return false;
    }

    private void initBean() {
        Set<SootClass> initBeans = new HashSet<>(singletonComponents);
        initBeans.addAll(beansAndMethods.keySet());
        if (collect != null && collect.size() > 0) {
            for (String s : collect.keySet()) {
                initBeans.add(Scene.v().getSootClass(s));
            }
        }
        GenerateSyntheticClass gsc = new GenerateSyntheticClassImpl();
        gsc.generateSingletonBeanFactory(initBeans, beansAndMethods.keySet(), collect);
        linkMainAndConfiguration();
    }

    private boolean isSkip(SootClass sootClass) {
        for (String exclude_class : FileUtil.getConfigString("exclude_class").split(";")) {
            if (sootClass.getName().startsWith(exclude_class)) {
                return true;
            }
        }
        return false;
    }

    private Set<SootMethod> findAllDefaultMethod(SootClass bean) {
        Set<SootMethod> defaultMethod = new HashSet<>();
        if (bean == null || bean.getName().equals(OBJECT_CLASS)) {
            return defaultMethod;
        }
        if (bean.isInterface()) {
            defaultMethod.addAll(bean.getMethods());
        } else {
            defaultMethod.addAll(findAllDefaultMethod(bean.getSuperclass()));
            for (SootClass anInterface : bean.getInterfaces()) {
                defaultMethod.addAll(findAllDefaultMethod(anInterface));
            }
        }
        return defaultMethod;
    }

    private boolean isDeclareMethod(SootMethod targetMethod, SootClass bean) {
        if (bean.getName().equals(OBJECT_CLASS)) {
            return false;
        }
        if (bean.declaresMethod(targetMethod.getSubSignature())) {
            return true;
        } else {
            return isDeclareMethod(targetMethod, bean.getSuperclass());
        }
    }

    private void implMapper(GenerateSyntheticClass gsc, DBMethodBean databaseModel) {
        SootClass sootClass = databaseModel.getSootClass();
        SootClass mapperImplClass = gsc.generateMapperImpl(sootClass, databaseModel);
        interfaceToBeans.put(sootClass.getName(), mapperImplClass);
        allBeansAndInterfaces.add(sootClass);
        if (CreateEdge.prototypeInterfaces.contains(sootClass)) {
            prototypeComponents.add(mapperImplClass);
        } else {
            singletonComponents.add(mapperImplClass);
        }
    }

    private void findAllSuperclass(SootClass sootClass) {
        for (SootClass superClassesAndInterface : SootUtil.getSuperClassesAndInterfaces(sootClass)) {
            if (superClassesAndInterface.getName().equals(OBJECT_CLASS)) continue;
            interfaceToBeans.put(superClassesAndInterface.getName(), sootClass);
            allBeansAndInterfaces.add(superClassesAndInterface);
            hasBeenProcess.add(superClassesAndInterface);
        }
    }

    private void dependencyInjectAnalysis() {
        IOCParser iocParser = new IOCParser();
        for (SootClass sootClass : allComponents) {
            if (sootClass.getName().contains("SingletonFactory")
                    || sootClass.getName().startsWith("synthetic.")
                    || sootClass.isInterface()) {
                continue;
            }
            iocParser.getIOCObject(sootClass, beansAndMethods.values());
        }
    }

    private void generateEntryPoints() {
        SootMethod psm = findMainMethod();
        if (psm == null) {
            SootClass sClass = new SootClass(dummyClassName, Modifier.PUBLIC);
            sClass.setSuperclass(Scene.v().getSootClass(OBJECT_CLASS));
            Scene.v().addClass(sClass);
            sClass.setApplicationClass();
            SootMethod mainMethod = new SootMethod("main",
                    Arrays.asList(new Type[]{ArrayType.v(RefType.v("java.lang.String"), 1)}),
                    VoidType.v(), Modifier.PUBLIC | Modifier.STATIC);
            sClass.addMethod(mainMethod);
            mainMethod.setActiveBody(jimpleUtil.createMainJimpleBody(mainMethod));
            psm = mainMethod;
            SootMethod initMethod = jimpleUtil.genDefaultConstructor(sClass);
            sClass.addMethod(initMethod);
        }
        projectMainMethod = psm;
    }

    public void invokeController() {
        JimpleBody body = (JimpleBody) projectMainMethod.retrieveActiveBody();
        PatchingChain<Unit> units = body.getUnits();
        units.removeIf(unit -> unit instanceof JReturnVoidStmt);
        List<Type> params = new ArrayList<>();
        Local httpServletRequest = jimpleUtil.mockParamOfHttpServlet(body, body.getUnits(), Scene.v().getSootClass("javax.servlet.http.HttpServletRequest"));
        Local httpServletResponse = jimpleUtil.mockParamOfHttpServlet(body, body.getUnits(), Scene.v().getSootClass("javax.servlet.http.HttpServletResponse"));
        Local httpSession = jimpleUtil.mockParamOfHttpSession(body, body.getUnits(), Scene.v().getSootClass("javax.servlet.http.HttpSession"));
        params.add(httpServletRequest.getType());
        params.add(httpServletResponse.getType());
        params.add(httpSession.getType());
        for (SootClass controller : AnnotationAnalysis.controllers) {
            linkMainAndController(controller, params);
        }

        if (!(units.getLast() instanceof JReturnVoidStmt)) {
            jimpleUtil.addVoidReturnStmt(units);
        }
    }

    private SootMethod findMainMethod() {
        Collection<SootClass> elementsUnsorted = sootAppClassChain.getElementsUnsorted();
        for (SootClass sootClass : elementsUnsorted) {
            if (SootUtil.isSpecialClass(sootClass)) {
                continue;
            }
            List<SootMethod> sootMethods = sootClass.getMethods();
            if (annotationAnalysis.hasSpecialAnnotation(sootClass, ANNOTATION_SPRING_BOOT) != null) {
                for (SootMethod sootMethod : sootMethods) {
                    if (sootMethod.getName().equals("main")) {
                        return sootMethod;
                    }
                }
            } else if (sootMethods.size() > 1) {
                for (SootMethod sootMethod : sootMethods) {
                    if (sootMethod.getSubSignature().contains("void main(") && sootMethod.isStatic()) {
                        for (Unit unit : sootMethod.retrieveActiveBody().getUnits()) {
                            if (unit.toString().contains("SpringApplication")) {
                                return sootMethod;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }


    private void linkMainAndController(SootClass controller, List<Type> paramList) {
        SootMethod psm = projectMainMethod;
        List<Type> params = new ArrayList<>(paramList);
        List<SootMethod> callEntryList = new ArrayList<>();
        SootClass sootClass = BasicCoRTransformer.proxyClassMap.getOrDefault(controller.getName(), controller);

        for (int i = 0; i < controller.getMethods().size(); i++) {
            SootMethod method = controller.getMethods().get(i);
            SootMethod methodMapOrDefault = BasicCoRTransformer.proxyMethodMap.getOrDefault(method.getSignature(), method);
            if (methodMapOrDefault == psm) {
                continue;
            }
            AnnotationTag requestMapping = annotationAnalysis.hasSpecialAnnotation(method);
            if (requestMapping != null) {
                List<SootMethod> signatures = new ArrayList<>();
                SootMethod createMethod;
                if (!sootClass.declaresMethod("callEntry_synthetic_" + method.getName(), params, VoidType.v())) {
                    createMethod = new SootMethod("callEntry_synthetic_" + method.getName(), params, VoidType.v(), Modifier.PUBLIC);
                    sootClass.addMethod(createMethod);
                } else {
                    createMethod = sootClass.getMethodByName("callEntry_synthetic_" + method.getName());
                }
                if (sootClass == controller) {
                    signatures.add(method);
                } else {
                    signatures.add(methodMapOrDefault);
                }
                createMethod.setActiveBody(jimpleUtil.createJimpleBody(createMethod, signatures, sootClass.getName()));
                callEntryList.add(createMethod);
            }
        }
        if (callEntryList.size() != 0) {
            String methodName = jimpleUtil.getInitMethod(sootClass).getSignature();
            processMain(psm, sootClass.getShortName(), sootClass.getName(), methodName, callEntryList);
        }
    }

    private void linkMainAndConfiguration() {
        SootMethod psm = projectMainMethod;
        SootMethod createMethod = new SootMethod("initBean_synthetic", null, VoidType.v(), Modifier.PUBLIC);
        psm.getDeclaringClass().addMethod(createMethod);
        SootClass singletonFactory = Scene.v().getSootClass("synthetic.method.SingletonFactory");
        ArrayList<SootMethod> beanList = new ArrayList<>();
        for (String needInitBean : needInitBeans) {
            beanList.add(singletonFactory.getMethod(needInitBean));
        }
        JimpleBody jimpleBody = jimpleUtil.createJimpleBody(createMethod, beanList, psm.getDeclaringClass().getName());
        createMethod.setActiveBody(jimpleBody);
        String initMethodName = psm.getDeclaringClass().getMethodByName("<init>").getSignature();
        processMain(psm, psm.getDeclaringClass().getShortName(), psm.getDeclaringClass().getName(), initMethodName, new ArrayList<>(Collections.singleton(createMethod)));
    }

    private void DAOPatternAnalysis() {
        GenerateSyntheticClass gsc = new GenerateSyntheticClassImpl();
        DBAnalysis dbAnalysis = new DBAnalysis();
        Set<DBMethodBean> databaseModels = dbAnalysis.findDBMethods(sootAppClassChain, DBSootClasses);
        for (DBMethodBean databaseModel : databaseModels) {
            implMapper(gsc, databaseModel);
        }
        for (SootClass bean : allBeansAndInterfaces) {
            if (bean.isAbstract() || bean.isInterface()) {
                continue;
            }
            for (SootMethod defaultMethod : findAllDefaultMethod(bean)) {
                if (!isDeclareMethod(defaultMethod, bean)) {
                    SootMethod newMethod = new SootMethod(defaultMethod.getName(), defaultMethod.getParameterTypes(),
                            defaultMethod.getReturnType(), defaultMethod.getModifiers());
                    newMethod.setActiveBody((Body) defaultMethod.retrieveActiveBody().clone());
                    bean.addMethod(newMethod);
                }
            }
        }
    }

    private void CoRPatternAnalysis() {
        CoRParser();
        CoRTransformer();
        FrameworkModelingEngine fme = new FrameworkModelingEngine();
        for (HandlerTargetModel targetModel : CoRParser.handlerAndTargetModelMap.values()) {
            fme.frameworkModeling(targetModel);
        }
    }

    private void CoRParser() {
        Set<SootClass> hasBeenParser = new HashSet<>();
        AOPParser aopParser = new AOPParser();
        SpringSecurityParser securityParser = new SpringSecurityParser();
        InterceptorParser interceptorParser = new InterceptorParser();
        FilterParser filterParser = new FilterParser();
        ShiroParser shiroParser = new ShiroParser();

        AnnotationAnalysis.configurations.removeAll(AnnotationAnalysis.removeConfigurations);
        for (SootClass configuration : AnnotationAnalysis.configurations) {
            if (AnnotationAnalysis.matchAnnotation(configuration, ANNOTATION_TYPE_ASPECT)) {
                aopParser.process(configuration);
            } else {
                SootClass processClass = configuration;
                Set<String> methodSigString = new HashSet<>();
                do {
                    if (hasBeenParser.contains(processClass)) {
                        break;
                    }
                    for (SootMethod method : processClass.getMethods()) {
                        if (removeConfigurationsMethod.contains(method) || methodSigString.contains(method.getSubSignature())) {
                            methodSigString.add(method.getSubSignature());
                            continue;
                        }
                        if (filterParser.matchConfigMethod(method)) {
                            filterParser.process(method);
                        } else if (shiroParser.matchConfigMethod(method)) {
                            shiroParser.process(method);
                        } else if (interceptorParser.matchConfigMethod(method)) {
                            interceptorParser.process(method);
                        } else if (securityParser.matchConfigMethod(method)) {
                            securityParser.process(method);
                            hasBeenParser.addAll(SootUtil.getAllSuperClasses(method.getDeclaringClass()));
                        }
                        methodSigString.add(method.getSubSignature());
                    }
                    hasBeenParser.add(processClass);
                    processClass = processClass.getSuperclass();
                } while (!AnnotationAnalysis.configurations.contains(processClass) && processClass.hasSuperclass());
            }
        }

        Set<String> web_xml_paths = FileUtil.getBeanXmlPaths("web_xml_paths");
        if (web_xml_paths.size() > 0) {
            filterParser.getXMLFilterSootClazzes(web_xml_paths);
        }
        Set<String> shiro_xml_paths = FileUtil.getBeanXmlPaths("shiro_xml_paths");
        if (shiro_xml_paths.size() > 0) {
            shiroParser.getXMLFilterSootClazzes(shiro_xml_paths);
        }
        for (HandlerModel handlerModel : CoRParser.handlerMap.values()) {
            handlerModel.getCoRParser().constructHandlerChain(handlerModel);
        }
    }

    private void CoRTransformer() {
        for (HandlerTargetModel handlerTargetModel : CoRParser.handlerAndTargetModelMap.values()) {
            Collections.sort(handlerTargetModel.getHandlerChain());
            for (HandlerModelEnum handlerModelEnum : handlerTargetModel.getFrameworkCategory()) {
                FactoryUtil.getCoRTransformer(handlerModelEnum).addAdviceToTarget(handlerTargetModel);
            }
        }
    }

    private XMLDocumentHolder getXMLHolder(Set<String> xmlpaths) {
        if (xmlpaths.size() == 0) {
            return null;
        }
        XMLDocumentHolder holder = new XMLDocumentHolder();
        for (String xmlpath : xmlpaths) {
            org.dom4j.Document document = holder.getDocument(xmlpath);
            if (document != null) {
                holder.addElements(document);
                holder.hasArgConstructorBean(document);
            }
        }
        return holder;
    }

    public Set<XmlBeanClazz> getXMLBeanSootClazzes(Set<String> xmlpaths) {
        XMLDocumentHolder holder = getXMLHolder(xmlpaths);
        if (holder == null) {
            return null;
        }
        Map<String, String> allClassMap = holder.getAllClassMap();
        Set<XmlBeanClazz> res = new HashSet<>();
        for (String value : allClassMap.values()) {
            String[] split = value.split(";");
            XmlBeanClazz xmlBeanClazz = new XmlBeanClazz(Scene.v().getSootClass(split[0]), split[1]);
            res.add(xmlBeanClazz);
        }
        return res;
    }



    public void processMain(SootMethod method, String objName, String objType, String initSign, List<SootMethod> runSignList) {
        JimpleBody body = (JimpleBody) method.retrieveActiveBody();
        Local localModel = jimpleUtil.addLocalVar(objName, objType, body);
        PatchingChain<Unit> units = body.getUnits();
        units.removeIf(unit -> unit instanceof JReturnVoidStmt);
        jimpleUtil.createAssignStmt(localModel, objType, units);
        units.add(jimpleUtil.specialCallStatement(localModel, initSign));
        for (SootMethod runSign : runSignList) {
            units.add(jimpleUtil.virtualCallStatement(localModel, runSign));
        }
        jimpleUtil.addVoidReturnStmt(units);
    }
}
