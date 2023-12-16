package utils;

import soot.*;
import soot.jimple.StaticFieldRef;
import soot.tagkit.SignatureTag;
import soot.tagkit.Tag;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.StringConstantUtil.OBJECT_CLASS;

public class SootUtil {
    private static final Map<String, Set<SootClass>> superClassAndInterfaceSet = new HashMap<>();
    private static final Map<String, List<SootClass>> superClassList = new HashMap<>();
    private static final Map<String, List<SootClass>> subClassAndImplementList = new HashMap<>();
    private static int firstState = Scene.v().getState();
    private static Hierarchy hierarchy = new Hierarchy();
    public static Map<SootClass, Set<SootClass>> classAndInnerClass = new HashMap<>();

    public static boolean matchClassAndMethod(SootMethod method, String className, String methodName) {
        return method.getName().equals(methodName) && method.getDeclaringClass().getName().equals(className);
    }

    public static boolean matchMethod(SootMethod method, String subSigMethod) {
        return method.getSubSignature().equals(subSigMethod);
    }

    public static boolean matchClass(StaticFieldRef ref, String className) {
        String actClassName = ref.getField().getDeclaringClass().getName();
        return actClassName.equals(className);
    }

    public static boolean matchClass(SootClass sc, String name) {
        return sc.getName().contains(name);
    }

    public static boolean matchInterface(SootClass sc, String name) {
        for (SootClass anInterface : sc.getInterfaces()) {
            if (SootUtil.matchClass(anInterface, name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchSuperClass(SootClass sc, String name) {
        return sc.getSuperclass().getName().equals(name);
    }

    public static boolean matchType(Type type, String typeName) {
        return type.toString().equals(typeName);
    }

    public static SootClass getSootClassByName(String name) {
        return Scene.v().getSootClass(name);
    }

    public static String getHashCode(String name) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] result = md.digest();
            return new BigInteger(1, result).toString(16);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static boolean isSpecialClass(SootClass sootClass) {
        if (sootClass.getPackageName().contains("org.springframework")) {
            return true;
        }
        return false;
    }

    public static SootMethod findSpecialMethodIgnoreCase(String name, SootClass sootClass) {
        if (sootClass.getName().equals(OBJECT_CLASS)) {
            return null;
        }
        for (SootMethod method : sootClass.getMethods()) {
            if (method.getName().equalsIgnoreCase(name)) {
                return method;
            }
        }
        return findSpecialMethodIgnoreCase(name, sootClass.getSuperclass());
    }

    public static SootField findSpecialFieldIgnoreCase(String name, SootClass sootClass) {
        if (name.contains(".")) {
            String[] fieldNames = name.split("\\.");
            SootField outerField = findSpecialFieldIgnoreCase(fieldNames[0], sootClass);
            if (outerField != null && outerField.getType() instanceof RefType) {
                return findSpecialFieldIgnoreCase(fieldNames[1], ((RefType) outerField.getType()).getSootClass());
            }
        }
        if (sootClass == null || sootClass.getName().equals(OBJECT_CLASS)) {
            return null;
        }
        for (SootField field : sootClass.getFields()) {
            if (field.getName().equalsIgnoreCase(name)) {
                return field;
            }
        }
        return findSpecialFieldIgnoreCase(name, sootClass.getSuperclass());
    }

    public static String lowerFirst(String name) {
        char[] chars = name.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }

    public static String upperFirst(String name) {
        char[] chars = name.toCharArray();
        chars[0] -= 32;
        return String.valueOf(chars);
    }

    public static List<SootClass> getAllSuperClasses(SootClass sootClass) {
        if (superClassList.containsKey(sootClass.getName())) {
            return superClassList.get(sootClass.getName());
        }
        List<SootClass> superclasses = new ArrayList<>();
        SootClass current = sootClass;
        superclasses.add(current);
        while (current.hasSuperclass()) {
            superclasses.add(current.getSuperclass());
            current = current.getSuperclass();
        }
        superClassList.put(sootClass.getName(), superclasses);
        return superclasses;
    }

    private static Set<SootClass> getAllInterfacesAndSuperClasses(SootClass sootClass) {
        SootClass current = sootClass;
        Set<SootClass> allClasses = new HashSet<>(current.getInterfaces());
        for (SootClass anInterface : current.getInterfaces()) {
            allClasses.addAll(getAllInterfacesAndSuperClasses(anInterface));
        }
        allClasses.add(current);
        while (current.hasSuperclass()) {
            allClasses.add(current.getSuperclass());
            current = current.getSuperclass();
            allClasses.addAll(current.getInterfaces());
            for (SootClass anInterface : current.getInterfaces()) {
                allClasses.addAll(getAllInterfacesAndSuperClasses(anInterface));
            }
        }
        return allClasses;
    }

    public static Set<SootClass> getSuperClassesAndInterfaces(SootClass sootClass) {
        if (superClassAndInterfaceSet.containsKey(sootClass.getName())) {
            return superClassAndInterfaceSet.get(sootClass.getName());
        }
        Set<SootClass> allClass = getAllInterfacesAndSuperClasses(sootClass);
        superClassAndInterfaceSet.put(sootClass.getName(), allClass);
        return allClass;
    }

    public static SootClass findGenericsClass(SootMethod targetMethod, SootClass genericsClass) {
        SootClass realMapper = genericsClass;
        for (Tag tag : targetMethod.getTags()) {
            if (tag instanceof SignatureTag) {
                String listType = ((SignatureTag) tag).getSignature();
                Pattern pattern = Pattern.compile("/" + genericsClass.getShortName() + "<[A-Za-z/;]*>");
                Matcher typeMatcher = pattern.matcher(listType);
                while (typeMatcher.find()) {
                    String[] typeArray = typeMatcher.group(0).trim().replace("/" + genericsClass.getShortName(), "").replace("/", ".").split(";");
                    String type = typeArray[0].replaceAll("[<;>]", "").substring(1);
                    realMapper = Scene.v().getSootClass(type);
                }
                break;
            }
        }
        return realMapper;
    }

    public static List<SootClass> getSubClassOrImplementClass(SootClass sootClass) {
        if (firstState != Scene.v().getState()) {
            firstState = Scene.v().getState();
            hierarchy = new Hierarchy();
        }
        List<SootClass> hierarchyClasses;
        if (subClassAndImplementList.containsKey(sootClass.getName())) {
            hierarchyClasses = subClassAndImplementList.get(sootClass.getName());
        } else {
            hierarchyClasses = sootClass.isInterface() ? hierarchy.getImplementersOf(sootClass) : hierarchy.getSubclassesOf(sootClass);
            subClassAndImplementList.put(sootClass.getName(), hierarchyClasses);
        }
        return hierarchyClasses;
    }
}
