package frontend;

import analysis.CreateEdge;
import bean.AopXMLResultBean;
import bean.HandlerModel;
import org.aspectj.weaver.UnresolvedType;
import org.aspectj.weaver.patterns.*;
import soot.*;
import soot.jimple.Stmt;
import soot.tagkit.*;
import transformer.HandlerEnum;
import transformer.HandlerModelEnum;
import utils.EnumUtil;
import utils.FileUtil;
import utils.SootUtil;
import utils.XMLDocumentHolder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class AOPParser extends CoRParser {
    public static Map<String, HandlerModel> aspectModelMap = new HashMap<>();

    @Override
    public void process(AbstractHost host) {
        SootClass sootClass = (SootClass) host;
        handlerMap.put(sootClass, getHandler(sootClass));
    }

    public HandlerModel getHandler(SootClass sootClass) {
        int order = Integer.MAX_VALUE;
        VisibilityAnnotationTag annotationTags = AnnotationAnalysis.getVisibilityAnnotationTag(sootClass);
        if (annotationTags != null && annotationTags.getAnnotations() != null) {
            for (AnnotationTag annotation : annotationTags.getAnnotations()) {
                if (annotation.getType().equals("Lorg/aspectj/lang/annotation/Order;")) {
                    for (AnnotationElem elem : annotation.getElems()) {
                        if (elem instanceof AnnotationIntElem) {
                            order = ((AnnotationIntElem) elem).getValue();
                        }
                    }
                }
            }
        }
        return new HandlerModel(sootClass, order, this, HandlerModelEnum.AOP);
    }

    @Override
    public void constructHandlerChain(HandlerModel handlerModel) {
        HashMap<String, String> pointcutMethod = new HashMap<>();
        for (SootMethod method : handlerModel.getSootClass().getMethods()) {
            VisibilityAnnotationTag annotationTags = AnnotationAnalysis.getVisibilityAnnotationTag(method);
            if (annotationTags != null && annotationTags.getAnnotations() != null) {
                for (AnnotationTag annotation : annotationTags.getAnnotations()) {
                    if (annotation.getType().contains("Pointcut")) {
                        for (AnnotationElem elem : annotation.getElems()) {
                            pointcutMethod.put(method.getName(), ((AnnotationStringElem) elem).getValue());
                        }
                    }
                }
            }
        }

        for (SootMethod aspectMethod : handlerModel.getSootClass().getMethods()) {
            VisibilityAnnotationTag annotationTags = AnnotationAnalysis.getVisibilityAnnotationTag(aspectMethod);
            if (annotationTags == null) {
                continue;
            }
            for (AnnotationTag annotation : annotationTags.getAnnotations()) {
                if (annotation.getType().contains("Lorg/aspectj/lang/annotation/")) {
                    if (annotation.getType().contains("Pointcut") || annotation.getType().contains("AfterThrowing"))
                        break;
                    for (AnnotationElem elem : annotation.getElems()) {
                        AnnotationStringElem ase = (AnnotationStringElem) elem;
                        if (!ase.getName().equals("value") && !ase.getName().equals("pointcut")) {
                            continue;
                        }
                        String expression = ase.getValue();
                        for (String s : pointcutMethod.keySet()) {
                            if (expression.contains(s)) {
                                expression = expression.replace(s + "()", pointcutMethod.get(s));
                            }
                        }
                        if ((expression.contains("execution") || expression.contains("within")
                                || expression.contains("args")) || expression.contains("@annotation") || expression.contains("@within")) {
                            processDiffAopExp(handlerModel, expression, aspectMethod, annotation);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void caseStart(Stmt stmt) {

    }

    @Override
    public void caseFoundHandler(Stmt stmt) {

    }

    @Override
    public void casePattern(Stmt stmt) {

    }

    @Override
    void putAddPathPattern(HandlerModel handlerModel) {

    }

    @Override
    boolean pathMatch(List<String> pathPatterns, String httpMethod, String annotationPath) {
        return false;
    }

    @Override
    void addPathForMethod(String path, SootMethod method, HandlerModel handlerModel) {

    }

    @Override
    public boolean matchConfigMethod(SootMethod method) {
        return false;
    }

    public void processDiffAopExp(HandlerModel configurationModel, String expression, SootMethod aspectMethod, AnnotationTag annotation) {
        PatternParser parser = new PatternParser(expression);
        Pointcut pointcut = parser.parsePointcut();
        AOPParser aopParser = new AOPParser();
        for (SootClass sootClass : CreateEdge.allComponents) {
            if (sootClass.isAbstract() || sootClass.isInterface()) {
                continue;
            }
            for (SootMethod targetMethod : sootClass.getMethods()) {
                if (aopParser.switchPoint(pointcut, aspectMethod, targetMethod)) {
                    HandlerModel adviceModel = getAspectModelInstance(configurationModel, expression, annotation, aspectMethod);
                    savePointMethod(adviceModel, sootClass, targetMethod);
                }
            }
        }
    }

    public HandlerModel getAspectModelInstance(HandlerModel aspect, String expression, AnnotationTag annotation, SootMethod aspectMethod) {
        HandlerModel adviceModel;
        if (aspectModelMap.containsKey(aspectMethod.toString())) {
            adviceModel = aspectModelMap.get(aspectMethod.toString());
            adviceModel.addPointcutExpressions(expression);
        } else {
            adviceModel = new HandlerModel(this);
            adviceModel.setOrder(aspect.getOrder());
            adviceModel.setSootClass(aspect.getSootClass());
            adviceModel.addPointcutExpressions(expression);
            adviceModel.setSootMethod(aspectMethod);
            adviceModel.setAnnotation(EnumUtil.getEnumObjectForAOP(annotation.getType()));
            adviceModel.setHandlerModelEnum(HandlerModelEnum.AOP);
            adviceModel.setSuperClassList(SootUtil.getAllSuperClasses(aspect.getSootClass()));
            aspectModelMap.put(aspectMethod.toString(), adviceModel);
        }
        return adviceModel;
    }

    public boolean switchPoint(Pointcut pointcut, SootMethod aspectMethod, SootMethod targetMethod) {
        boolean matched = false;
        switch (pointcut.getClass().getSimpleName()) {
            case "WithinPointcut":
                matched = withInProcess((WithinPointcut) pointcut, targetMethod);
                break;
            case "KindedPointcut":
                matched = executionProcess((KindedPointcut) pointcut, targetMethod);
                break;
            case "ArgsPointcut":
                matched = ArgsProcess((ArgsPointcut) pointcut, targetMethod);
                break;
            case "AndPointcut":
                matched = andProcess((AndPointcut) pointcut, aspectMethod, targetMethod);
                break;
            case "OrPointcut":
                matched = orProcess((OrPointcut) pointcut, aspectMethod, targetMethod);
                break;
            case "AnnotationPointcut":
                matched = AnnoProcess((AnnotationPointcut) pointcut, aspectMethod, targetMethod);
                break;
            case "WithinAnnotationPointcut":
                matched = withinAnnoProcess((WithinAnnotationPointcut) pointcut, targetMethod);
                break;
        }
        return matched;
    }

    private boolean withinAnnoProcess(WithinAnnotationPointcut withinAnnotationPointcut, SootMethod targetMethod) {
        AnnotationTypePattern typePattern = withinAnnotationPointcut.getAnnotationTypePattern();
        String str = "";
        if (typePattern instanceof ExactAnnotationTypePattern) {
            ExactAnnotationTypePattern ex = (ExactAnnotationTypePattern) typePattern;
            UnresolvedType annotationType = ex.getAnnotationType();
            str = annotationType.getSignature();
        }
        VisibilityAnnotationTag methodAnnotationTag = AnnotationAnalysis.getVisibilityAnnotationTag(targetMethod);
        if (methodAnnotationTag != null && methodAnnotationTag.getAnnotations() != null) {
            for (AnnotationTag annotation : methodAnnotationTag.getAnnotations()) {
                if (annotation.getType().equals(str)) {
                    return true;
                }
            }
        }
        VisibilityAnnotationTag classAnnotationTag = (VisibilityAnnotationTag) targetMethod.getDeclaringClass().getTag("VisibilityAnnotationTag");
        if (classAnnotationTag != null && classAnnotationTag.getAnnotations() != null) {
            for (AnnotationTag annotation : classAnnotationTag.getAnnotations()) {
                if (annotation.getType().equals(str)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean withInProcess(WithinPointcut pointcut, SootMethod targetMethod) {
        TypePattern typePattern = pointcut.getTypePattern();
        Map<String, Object> dealWithinPointcut = dealWithinPointcut(typePattern);
        Integer type = (Integer) dealWithinPointcut.get("type");
        switch (type) {
            case 1:
            case 2:
                break;
            case 3:
                NamePattern[] namePatterns1 = (NamePattern[]) dealWithinPointcut.get("pattern");
                if (!clazzIsMatch(namePatterns1, targetMethod.getDeclaringClass().getName())) {
                    return false;
                }
                break;
        }
        return true;
    }

    public boolean ArgsProcess(ArgsPointcut pointcut, SootMethod targetMethod) {
        TypePattern[] typePatterns = pointcut.getArguments().getTypePatterns();
        return isMethodParamMatches(typePatterns, targetMethod.getParameterTypes());

    }

    public boolean executionProcess(KindedPointcut pointcut, SootMethod targetMethod) {
        SignaturePattern pattern = pointcut.getSignature();
        String modifier = pattern.getModifiers().toString();
        TypePattern declaringType = pattern.getDeclaringType();
        TypePattern returnType = pattern.getReturnType();
        NamePattern methodName = pattern.getName();
        TypePattern[] typePatterns = pattern.getParameterTypes().getTypePatterns();
        if (declaringType instanceof WildTypePattern) {
            WildTypePattern wildType = (WildTypePattern) declaringType;
            NamePattern[] namePatterns = wildType.getNamePatterns();
            if (!clazzIsMatch(namePatterns, targetMethod.getDeclaringClass().getName())) {
                return false;
            }
        }

        int methodModifier = targetMethod.getModifiers();
        boolean flag;
        switch (modifier) {
            case "public":
                flag = Modifier.isPublic(methodModifier);
                break;
            case "protected":
                flag = Modifier.isProtected(methodModifier);
                break;
            case "private":
                flag = Modifier.isPrivate(methodModifier);
                break;
            default:
                flag = true;
                break;
        }

        if (!flag || !methodName.matches(targetMethod.getName()) || targetMethod.getName().equals("<init>")
                || targetMethod.getName().equals("callEntry_synthetic") || targetMethod.getName().equals("<clinit>")) {
            return false;
        }

        if (returnType instanceof WildTypePattern) {
            WildTypePattern wildType = (WildTypePattern) returnType;
            NamePattern[] namePatterns = wildType.getNamePatterns();
            if (clazzIsMatch(namePatterns, targetMethod.getReturnType().toString())) {
                return false;
            }
        }

        return isMethodParamMatches(typePatterns, targetMethod.getParameterTypes());
    }

    public boolean AnnoProcess(AnnotationPointcut pointcut, SootMethod aspectMethod, SootMethod targetMethod) {
        String s = pointcut.getAnnotationTypePattern().getAnnotationType().toString();
        String annot = "";
        for (Local local : aspectMethod.retrieveActiveBody().getLocals()) {
            if (local.getName().equals(s)) {
                String a = local.getType().toString();
                String[] array = a.split("\\.");
                annot = array[array.length - 1];
                break;
            }
        }
        s = s.substring(s.lastIndexOf(".") + 1);
        boolean isclazzAnnoed = false;
        VisibilityAnnotationTag classAnnotationTag = AnnotationAnalysis.getVisibilityAnnotationTag(targetMethod.getDeclaringClass());
        if (classAnnotationTag != null && classAnnotationTag.getAnnotations() != null) {
            for (AnnotationTag annotation : classAnnotationTag.getAnnotations()) {
                String c = annotation.getType().substring(annotation.getType().lastIndexOf("/") + 1, annotation.getType().length() - 1);
                if (c.equals(s)) {
                    isclazzAnnoed = true;
                    break;
                }
            }
        }
        if (isclazzAnnoed) {
            return true;
        } else {
            VisibilityAnnotationTag methodAnnotationTags = AnnotationAnalysis.getVisibilityAnnotationTag(targetMethod);
            if (methodAnnotationTags != null && methodAnnotationTags.getAnnotations() != null) {
                for (AnnotationTag annotation : methodAnnotationTags.getAnnotations()) {
                    String c = annotation.getType().substring(annotation.getType().lastIndexOf("/") + 1, annotation.getType().length() - 1);
                    if (c.equals(s) || c.equals(annot)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }


    public boolean orProcess(OrPointcut pointcut, SootMethod aspectMethod, SootMethod targetMethod) {
        Pointcut leftPoint = pointcut.getLeft();
        Pointcut rightPoint = pointcut.getRight();
        return switchPoint(leftPoint, aspectMethod, targetMethod) || switchPoint(rightPoint, aspectMethod, targetMethod);
    }

    public boolean andProcess(AndPointcut pointcut, SootMethod aspectMethod, SootMethod targetMethod) {
        Pointcut leftPoint = pointcut.getLeft();
        Pointcut rightPoint = pointcut.getRight();
        return switchPoint(leftPoint, aspectMethod, targetMethod) && switchPoint(rightPoint, aspectMethod, targetMethod);
    }

    public boolean clazzIsMatch(NamePattern[] namePatterns, String path) {
        Pattern re1 = Pattern.compile("[a-z|A-Z_]+[0-9]*");
        Pattern re2 = Pattern.compile("\\*");
        StringBuilder sb = new StringBuilder();
        sb.append("^");
        for (NamePattern namePattern : namePatterns) {
            Matcher m1 = re1.matcher(namePattern.toString());
            Matcher m2 = re2.matcher(namePattern.toString());
            if (m1.find()) {
                sb.append(namePattern);
                sb.append("\\.");
            } else if (m2.find()) {
                sb.append("([a-z|A-Z|_]+[0-9]*)\\.");
            } else if (namePattern.toString().equals("")) {
                sb.append("(((\\D+)(\\w*)\\.)+)?");
            }
        }
        String res = sb.toString();
        if (res.lastIndexOf(".") == res.length() - 1) {
            res = res.substring(0, res.lastIndexOf("\\."));
        }
        res += "$";
        Pattern compile = Pattern.compile(res);
        Matcher matcher = compile.matcher(path);
        return matcher.find();
    }

    public boolean isMethodParamMatches(TypePattern[] typePatterns, List<Type> parameterTypes) {
        boolean ismatch = false;
        if (parameterTypes.size() >= typePatterns.length) {
            if (parameterTypes.size() == 0) {
                ismatch = true;
            } else {
                for (int i = 0; i < typePatterns.length; i++) {
                    String tmptype = typePatterns[i].toString();
                    if (i == (typePatterns.length - 1) && typePatterns.length == parameterTypes.size()) {
                        if ("..".equals(tmptype) || parameterTypes.get(i).toString().contains(tmptype)) {
                            ismatch = true;
                        }
                    }
                    if ("*".equals(tmptype)) {
                        continue;
                    }
                    if ("..".equals(tmptype)) {
                        ismatch = true;
                        break;
                    }
                    if (!parameterTypes.get(i).toString().contains(tmptype)) {
                        ismatch = false;
                        break;
                    }

                }
            }

        } else {
            int i;
            for (i = 0; i < parameterTypes.size(); i++) {
                String tmptype = typePatterns[i].toString();
                if ("*".equals(tmptype)) {
                    continue;
                }
                if ("..".equals(tmptype)) {
                    ismatch = true;
                    break;
                }
                if (!parameterTypes.get(i).toString().contains(tmptype)) {
                    break;
                }
            }
            if (typePatterns.length - i == 1 && "..".equals(typePatterns[typePatterns.length - 1].toString())) {
                ismatch = true;
            }

        }
        return ismatch;

    }

    public static Map<String, Object> dealWithinPointcut(TypePattern typePattern) {
        Map<String, Object> res = new HashMap<>();
        if (typePattern.isIncludeSubtypes()) {
            WildTypePattern wildTypePattern = (WildTypePattern) typePattern;
            NamePattern[] namePatterns = wildTypePattern.getNamePatterns();
            res.put("pattern", namePatterns);
            res.put("type", 1);
            return res;
        } else {
            if (typePattern instanceof AnyWithAnnotationTypePattern) {
                AnyWithAnnotationTypePattern awatp = (AnyWithAnnotationTypePattern) typePattern;
                WildAnnotationTypePattern wildAnnotationTypePattern = (WildAnnotationTypePattern) awatp.getAnnotationTypePattern();
                WildTypePattern wildTypePattern = (WildTypePattern) wildAnnotationTypePattern.getTypePattern();
                NamePattern[] namePatterns = wildTypePattern.getNamePatterns();
                res.put("pattern", namePatterns);
                res.put("type", 2);
                return res;
            } else {
                WildTypePattern wildTypePattern = (WildTypePattern) typePattern;
                NamePattern[] namePatterns = wildTypePattern.getNamePatterns();
                res.put("pattern", namePatterns);
                res.put("type", 3);
            }

        }
        return res;
    }

    private HandlerModel copyXmlBeanToAspectModel(AopXMLResultBean aopXMLResultBean, SootClass sootClass, SootMethod sootMethod) {
        HandlerModel adviceModel;
        if (aspectModelMap.containsKey(sootMethod.toString())) {
            adviceModel = aspectModelMap.get(sootMethod.toString());
            adviceModel.addPointcutExpressions(aopXMLResultBean.getExper());
        } else {
            adviceModel = new HandlerModel(new AOPParser());
            adviceModel.setHandlerModelEnum(HandlerModelEnum.AOP);
            adviceModel.setOrder(aopXMLResultBean.getOrder());
            adviceModel.setSootClass(sootClass);
            adviceModel.addPointcutExpressions(aopXMLResultBean.getExper());
            adviceModel.setSootMethod(sootMethod);
            adviceModel.setSuperClassList(SootUtil.getAllSuperClasses(sootClass));
            List<HandlerEnum> collect = Arrays.stream(HandlerEnum.values()).filter(x -> x.getAnnotationClassName().toLowerCase().contains(aopXMLResultBean.getActivetype())).collect(Collectors.toList());
            adviceModel.setAnnotation(collect.get(0));
            aspectModelMap.put(sootMethod.toString(), adviceModel);
        }
        return adviceModel;
    }

    private List<HandlerModel> xmlaspectAnalysis(String configPath, AOPParser aopParser) {
        XMLDocumentHolder holder = new XMLDocumentHolder();
        List<HandlerModel> allAdvices = new ArrayList<>();
        Set<String> bean_xml_paths = FileUtil.getBeanXmlPaths("bean_xml_paths");
        if (bean_xml_paths.size() == 0) {
            return allAdvices;
        }
        for (String bean_xml_path : bean_xml_paths) {
            org.dom4j.Document document = holder.getDocument(bean_xml_path);
            if (document == null) {
                continue;
            }
            holder.addElements(document);
            List<AopXMLResultBean> beanList = holder.processAopElements(document);
            Set<String> aopClasses = new HashSet<>();
            for (AopXMLResultBean aopXMLResultBean : beanList) {
                aopClasses.add(aopXMLResultBean.getAopclass());
            }
            for (String aopclass : aopClasses) {
                SootClass sootClass = Scene.v().getSootClass(aopclass);
                for (SootMethod method : sootClass.getMethods()) {
                    for (AopXMLResultBean aopXMLResultBean : beanList) {
                        if (method.getName().equals(aopXMLResultBean.getAopmethod())) {
                            HandlerModel aspectModel = copyXmlBeanToAspectModel(aopXMLResultBean, sootClass, method);
                            allAdvices.add(aspectModel);
                            Collections.sort(allAdvices);
                        }
                    }
                }
            }
        }
        return allAdvices;
    }
}
