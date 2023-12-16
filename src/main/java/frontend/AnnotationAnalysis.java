package frontend;

import analysis.CreateEdge;
import bean.MappingValueModel;
import soot.*;
import soot.tagkit.*;
import transformer.SpringAnnotationTag;
import utils.SootUtil;

import java.util.*;

import static analysis.CreateEdge.allBeansAndInterfaces;
import static analysis.CreateEdge.beansAndMethods;
import static utils.StringConstantUtil.*;

public class AnnotationAnalysis {
    public static List<Type> autoMethodParams = new ArrayList<>();
    public static Set<String> mapperPackages = new HashSet<>();
    public static Set<SootClass> controllers = new HashSet<>();
    public static Set<SootClass> configurations = new LinkedHashSet<>();
    public static Set<SootClass> removeConfigurations = new HashSet<>();
    public static Set<SootMethod> removeConfigurationsMethod = new HashSet<>();

    public List<Tag> getFieldTags(SootField field) {
        return field.getTags();
    }

    public SootField getFieldWithSpecialAnnos(SootField field, SootMethod initMethod) {
        for (Tag fieldTag : getFieldTags(field)) {
            String strTag = fieldTag.toString();
            if (strTag.contains("Autowired")
                    || strTag.contains("Qualifier")
                    || strTag.contains("Resource")
                    || strTag.contains("Inject")) {
                return field;
            }
        }
        if (autoMethodParams.contains(field.getType()) || initMethod.getParameterTypes().contains(field.getType())) {
            return field;
        }
        return null;
    }

    public List<Type> getParamOfAutoWiredMethod(SootMethod method) {
        VisibilityAnnotationTag methodTag = getVisibilityAnnotationTag(method);
        if (methodTag != null) {
            for (AnnotationTag annotation : methodTag.getAnnotations()) {
                if (annotation.getType().contains("Autowired")) {
                    return method.getParameterTypes();
                }
            }
        }
        return null;
    }

    public Integer getAllComponents(SootClass sootClassWithAnno, Set<String> preAnnoSet, SootClass sootClass) {
        VisibilityAnnotationTag annotationTags = getVisibilityAnnotationTag(sootClassWithAnno);
        int flag = 0;
        boolean hasClass = true;
        if (annotationTags != null) {
            for (AnnotationTag annotation : annotationTags.getAnnotations()) {
                switch (annotation.getType()) {
                    case ANNOTATION_TYPE_RESTCONTROLLER:
                    case ANNOTATION_TYPE_CONTROLLER:
                    case ANNOTATION_TYPE_SCONTROLLER:
                        controllers.add(sootClass);
                        if (!SpringAnnotationTag.isBean(flag)) {
                            flag += SpringAnnotationTag.BEAN;
                        }
                        break;
                    case ANNOTATION_TYPE_CONFIGURATION:
                    case ANNOTATION_TYPE_CONDITIONAL_ON_WEN:
                    case ANNOTATION_TYPE_ASPECT:
                        configurations.add(sootClass);
                        if (!SpringAnnotationTag.isBean(flag)) {
                            flag += SpringAnnotationTag.BEAN;
                        }
                        break;
                    case ANNOTATION_TYPE_CONDITIONAL:
                    case ANNOTATION_TYPE_CONDITIONAL_ON_CLASS:
                    case ANNOTATION_TYPE_CONDITIONAL_ON_BEAN:
                        for (AnnotationElem elem : annotation.getElems()) {
                            if (elem instanceof AnnotationArrayElem) {
                                for (AnnotationElem value : ((AnnotationArrayElem) elem).getValues()) {
                                    if (value instanceof AnnotationClassElem) {
                                        String oriString = ((AnnotationClassElem) value).getDesc();
                                        String conditionClassName = oriString.replace("/", ".").substring(1, oriString.length() - 1);
                                        if (!Scene.v().containsClass(conditionClassName)
                                                || Scene.v().getSootClass(conditionClassName).getMethods().size() < 1) {
                                            hasClass = false;
                                            flag = 0;
                                            removeConfigurations.add(sootClass);
                                            configurations.remove(sootClass);
                                            break;
                                        } else {
                                            // for (Tag tag : Scene.v().getSootClass(conditionClassName).getTags()) {
                                            //     if (tag instanceof InnerClassTag) {
                                            //         String innerClassName = ((InnerClassTag) tag).getInnerClass().replace("/", ".");
                                            //         SootClass innerClass = Scene.v().getSootClass(innerClassName);
                                            //         getAllComponents(innerClass, preAnnoSet, sootClass);
                                            //     }
                                            // }
                                        }
                                    }
                                }
                            }
                            if (!hasClass) {
                                break;
                            }
                        }
                        break;
                    case ANNOTATION_TYPE_CONDITIONAL_ON_MISSING_BEAN:
                        for (AnnotationElem elem : annotation.getElems()) {
                            if (elem instanceof AnnotationArrayElem) {
                                for (AnnotationElem value : ((AnnotationArrayElem) elem).getValues()) {
                                    if (value instanceof AnnotationClassElem) {
                                        String oriString = ((AnnotationClassElem) value).getDesc();
                                        String conditionClassName = oriString.replace("/", ".").substring(1, oriString.length() - 1);
                                        if (allBeansAndInterfaces.contains(Scene.v().getSootClass(conditionClassName))) {
                                            removeConfigurations.add(sootClass);
                                            configurations.remove(sootClass);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    case ANNOTATION_TYPE_CONDITIONAL_ON_PROPERTY:
                        StringBuilder propertyName = new StringBuilder();
                        getPropertyValue(annotation, propertyName);
                        if (CreateEdge.disableProperty.contains(propertyName.toString())) {
                            removeConfigurations.add(sootClass);
                            configurations.remove(sootClass);
                        }
                        break;
                    case ANNOTATION_TYPE_IMPORT:
                        if (removeConfigurations.contains(sootClass)) {
                            for (AnnotationElem elem : annotation.getElems()) {
                                if (elem instanceof AnnotationArrayElem) {
                                    for (AnnotationElem value : ((AnnotationArrayElem) elem).getValues()) {
                                        if (value instanceof AnnotationClassElem) {
                                            String oriString = ((AnnotationClassElem) value).getDesc();
                                            String conditionClassName = oriString.replace("/", ".").substring(1, oriString.length() - 1);
                                            SootClass importClass = Scene.v().getSootClass(conditionClassName);
                                            removeConfigurations.add(importClass);
                                            configurations.remove(importClass);
                                        }
                                    }
                                }
                            }
                        }
                        break;
                    case ANNOTATION_TYPE_MAPPER:
                    case ANNOTATION_TYPE_SQLRESOURCE:
                        if (!SpringAnnotationTag.isMapper(flag)) {
                            flag += SpringAnnotationTag.MAPPER;
                        }
                    case ANNOTATION_TYPE_COMPONENT:
                    case ANNOTATION_TYPE_REPOSITORY:
                    case ANNOTATION_TYPE_SERVICE:
                        if (!SpringAnnotationTag.isBean(flag)) {
                            flag += SpringAnnotationTag.BEAN;
                        }
                        break;
                    case ANNOTATION_TYPE_SCOPE:
                        for (AnnotationElem elem : annotation.getElems()) {
                            AnnotationStringElem stringElem = (AnnotationStringElem) elem;
                            if (stringElem.getValue().equals("prototype") && !SpringAnnotationTag.isPrototype(flag)) {
                                flag += SpringAnnotationTag.PROTOTYPE;
                            }
                        }
                        break;
                    case ANNOTATION_TYPE_MAPPERSCAN:
                        for (AnnotationElem elem : annotation.getElems()) {
                            AnnotationArrayElem arrayElem = (AnnotationArrayElem) elem;
                            for (AnnotationElem value : arrayElem.getValues()) {
                                AnnotationStringElem asElem = (AnnotationStringElem) value;
                                mapperPackages.add(asElem.getValue());
                            }
                        }
                        break;
                    case ANNOTATION_TYPE_DOCUMENTED:
                    case ANNOTATION_TYPE_RETENTION:
                    case ANNOTATION_TYPE_TARGET:
                        break;
                    default:
                        flag += findSpecialAnno(annotation, preAnnoSet, sootClass);
                }
            }
        }
        return flag;
    }

    private void getPropertyValue(AnnotationTag annotation, StringBuilder propertyName) {
        for (AnnotationElem elem : annotation.getElems()) {
            if (elem instanceof AnnotationStringElem && elem.getName().equals("prefix")) {
                propertyName.append(((AnnotationStringElem) elem).getValue());
            }
            if (elem instanceof AnnotationArrayElem && elem.getName().equals("name")) {
                for (AnnotationElem value : ((AnnotationArrayElem) elem).getValues()) {
                    if (value instanceof AnnotationStringElem) {
                        if (propertyName.length() > 0) {
                            propertyName.append(".");
                        }
                        propertyName.append(((AnnotationStringElem) value).getValue());
                        break;
                    }
                }
            }
        }
    }

    public void getAllBeans(SootClass sootClass) {
        for (SootMethod method : sootClass.getMethods()) {
            VisibilityAnnotationTag annotationTags = getVisibilityAnnotationTag(method);
            if (annotationTags != null) {
                SootClass bean = null;
                for (AnnotationTag annotation : annotationTags.getAnnotations()) {
                    switch (annotation.getType()) {
                        case "Lorg/springframework/context/annotation/Bean;":
                            bean = Scene.v().getSootClass(method.getReturnType().toString());
                            if (!beansAndMethods.containsKey(SootUtil.findGenericsClass(method, bean))) {
                                beansAndMethods.put(bean, method);
                            } else {
                                SootMethod deleteMethod = method;
                                if (sootClass.isApplicationClass() && !sootClass.getName().contains("springframework")) {
                                    deleteMethod = beansAndMethods.get(bean);
                                    beansAndMethods.put(bean, method);
                                }
                                // allBeansAndInterfaces.remove(deleteMethod.getDeclaringClass());
                                // removeConfigurations.add(deleteMethod.getDeclaringClass());
                                removeConfigurationsMethod.add(deleteMethod);
                            }
                            if (bean.isApplicationClass() && !bean.isAbstract() && !sootClass.getName().contains("springframework")) {
                                CreateEdge.interfaceToBeans.put(bean.getName(), bean);
                            }
                            break;
                        case ANNOTATION_TYPE_CONDITIONAL_ON_PROPERTY:
                            StringBuilder propertyName = new StringBuilder();
                            getPropertyValue(annotation, propertyName);
                            if (CreateEdge.disableProperty.contains(propertyName.toString())) {
                                removeConfigurationsMethod.add(method);
                                if (bean != null) {
                                    CreateEdge.interfaceToBeans.remove(bean.getName());
                                    beansAndMethods.remove(bean);
                                } else {
                                    break;
                                }
                            }
                        case ANNOTATION_TYPE_CONDITIONAL_ON_BEAN:
                            for (AnnotationElem elem : annotation.getElems()) {
                                if (elem instanceof AnnotationArrayElem) {
                                    for (AnnotationElem value : ((AnnotationArrayElem) elem).getValues()) {
                                        if (value instanceof AnnotationClassElem) {
                                            String oriString = ((AnnotationClassElem) value).getDesc();
                                            String conditionClassName = oriString.replace("/", ".").substring(1, oriString.length() - 1);
                                            if (!Scene.v().containsClass(conditionClassName)
                                                    || Scene.v().getSootClass(conditionClassName).getMethods().size() < 1) {
                                                removeConfigurationsMethod.add(method);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        default:
                            break;
                    }
                }
            }
        }
    }

    public Integer findSpecialAnno(AnnotationTag annotation, Set<String> preAnnoSet, SootClass sootClass) {
        if (preAnnoSet == null) {
            preAnnoSet = new HashSet<>();
        }
        String annoClassName = annotation.getType().replace("/", ".").replace(";", "");
        if (annoClassName.startsWith("L")) {
            annoClassName = annoClassName.substring(1);
        }
        if (!Scene.v().containsClass(annoClassName)) {
            return 0;
        }
        SootClass annotationClass = Scene.v().getSootClass(annoClassName);
        if (preAnnoSet.contains(annotationClass.toString())) {
            return 0;
        }
        preAnnoSet.add(annotationClass.toString());
        return getAllComponents(annotationClass, preAnnoSet, sootClass);
    }

    public AnnotationTag hasSpecialAnnotation(AbstractHost host) {
        VisibilityAnnotationTag annotationTags = getVisibilityAnnotationTag(host);
        if (annotationTags == null) {
            return null;
        }
        for (AnnotationTag annotation : annotationTags.getAnnotations()) {
            if (satisfyControllerAnnotation(annotation.getType())
                    || satisfyMappingAnnotation(annotation.getType())) {
                return annotation;
            }
        }
        return null;
    }

    public AnnotationTag hasSpecialAnnotation(AbstractHost host, String annotationTag) {
        VisibilityAnnotationTag annotationTags = getVisibilityAnnotationTag(host);
        if (annotationTags == null) {
            return null;
        }
        for (AnnotationTag annotation : annotationTags.getAnnotations()) {
            if (annotation.getType().equals(annotationTag)) {
                return annotation;
            }
        }
        return null;
    }

    public MappingValueModel getMappingAnnotationValue(AbstractHost host) {
        VisibilityAnnotationTag annotationTags = getVisibilityAnnotationTag(host);
        MappingValueModel mvm = new MappingValueModel();
        if (annotationTags == null) {
            return mvm;
        }
        for (AnnotationTag annotation : annotationTags.getAnnotations()) {
            if (satisfyMappingAnnotation(annotation.getType())) {
                for (AnnotationElem elem : annotation.getElems()) {
                    if (elem instanceof AnnotationArrayElem) {
                        AnnotationArrayElem aae = (AnnotationArrayElem) elem;
                        for (AnnotationElem value : aae.getValues()) {
                            if (aae.getName().equals("value") && value instanceof AnnotationStringElem) {
                                mvm.setPath(((AnnotationStringElem) value).getValue());
                            } else if (aae.getName().equals("method") && value instanceof AnnotationEnumElem) {
                                mvm.setHttpMethod(((AnnotationEnumElem) value).getConstantName());
                            }
                        }
                    }
                }
            }
        }
        return mvm;
    }

    public static VisibilityAnnotationTag getVisibilityAnnotationTag(AbstractHost host) {
        return (VisibilityAnnotationTag) host.getTag("VisibilityAnnotationTag");
    }

    private boolean satisfyControllerAnnotation(String type) {
        switch (type) {
            case ANNOTATION_TYPE_RESTCONTROLLER:
            case ANNOTATION_TYPE_CONTROLLER:
            case ANNOTATION_TYPE_SCONTROLLER:
                return true;
            default:
                return false;
        }
    }

    private boolean satisfyMappingAnnotation(String type) {
        switch (type) {
            case ANNOTATION_TYPE_REQUESTMAPPING:
            case ANNOTATION_TYPE_POSTMAPPING:
            case ANNOTATION_TYPE_GETMAPPING:
            case ANNOTATION_TYPE_PATCHMAPPING:
            case ANNOTATION_TYPE_DELETEMAPPING:
            case ANNOTATION_TYPE_PUTMAPPING:
                return true;
            default:
                return false;
        }
    }

    public static boolean matchAnnotation(AbstractHost host, String specialAnnotation) {
        VisibilityAnnotationTag annotationTags = getVisibilityAnnotationTag(host);
        if (annotationTags == null) {
            return false;
        }
        for (AnnotationTag annotation : annotationTags.getAnnotations()) {
            if (annotation.getType().equals(specialAnnotation)) {
                return true;
            }
        }
        return false;
    }
}
