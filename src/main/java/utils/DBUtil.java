package utils;

import bean.DBColumnBean;
import bean.DBMethodBean;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import soot.*;
import soot.tagkit.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.StringConstantUtil.*;

public class DBUtil {
    private final Pattern pattern = Pattern.compile("([$])\\{\\w*(\\.*)\\w*}");
    private final Pattern updateTableNamePattern = Pattern.compile("(update|UPDATE)(.+?)(set|SET)");
    private final Pattern updateParamPattern = Pattern.compile("(set|SET)\\s(.+?)(where|WHERE|;)");
    private final Pattern selectTableNamePattern = Pattern.compile("(FROM|from)\\s(\\$\\{)*(\\w*(\\.*)\\w*)(}*)");
    private final Pattern selectValuePattern = Pattern.compile("(select|SELECT)\\s((distinct|DISTINCT)?)(.+?)(FROM|from)");
    private final Pattern insertTableNamePattern = Pattern.compile("(into|INTO)\\s(\\w+)(\\s|\\()");
    private final Pattern insertParamPattern = Pattern.compile("(\\()(.+)(\\))(\\s?)(values|VALUES|select|SELECT)");
    private final Pattern insertValuePattern = Pattern.compile("(values|VALUES)(.+?)(where|WHERE|\\))");
    private final Pattern deleteTableNamePattern = Pattern.compile("(from|FROM)(.+?)(where|WHERE|;)");
    private final Pattern wherePattern = Pattern.compile("(where|WHERE)(.+?)(select|SELECT|;)");
    private final Pattern casePattern = Pattern.compile("(then|THEN)(.+?)(}|end|END|;)");
    public static Map<String, String> mapperAndSQL = new HashMap<>();
    public static Map<String, String> mapperCategory = new HashMap<>();
    public static Map<String, Map<String, String>> mapperAndResultMap = new HashMap<>();
    public static Map<String, SootClass> xmlReturnClass = new HashMap<>();
    public static Map<String, SootClass> xmlParamClass = new HashMap<>();


    public void resolveMybatisXml(Document document) {
        Element element = document.getDocumentElement();
        String namespace = element.getAttribute("namespace");
        NodeList childNodeList = element.getChildNodes();
        Map<String, String> sqlContent = new HashMap<>();
        Map<String, String> resultMap;
        Map<String, String> resultMapProperties = new HashMap<>();
        if (mapperAndResultMap.containsKey(namespace)) {
            resultMap = mapperAndResultMap.get(namespace);
        } else {
            resultMap = new HashMap<>();
            mapperAndResultMap.put(namespace, resultMap);
        }
        for (int temp = 0; temp < childNodeList.getLength(); temp++) {
            Node childNode = childNodeList.item(temp);
            String nodeName = childNode.getNodeName();
            switch (nodeName.toLowerCase()) {
                case "select":
                    String resultMapProperty = ((Element) childNode).getAttribute("resultMap");
                    String resultTypeProperty = ((Element) childNode).getAttribute("resultType");
                    SootClass resultClass;
                    if (!resultMapProperty.equals("")) {
                        if (resultMapProperties.containsKey(resultMapProperty)) {
                            resultMapProperty = resultMapProperties.get(resultMapProperty);
                        }
                        resultClass = Scene.v().getSootClass(resultMapProperty);
                        xmlReturnClass.put(namespace + "." + ((Element) childNode).getAttribute("id"), resultClass);
                    } else if (!resultTypeProperty.equals("")) {
                        resultClass = Scene.v().getSootClass(resultTypeProperty);
                        xmlReturnClass.put(namespace + "." + ((Element) childNode).getAttribute("id"), resultClass);
                    }
                case "update":
                case "delete":
                case "insert":
                    String parameterTypeProperty = ((Element) childNode).getAttribute("parameterType");
                    if (!parameterTypeProperty.equals("")) {
                        xmlParamClass.put(namespace + "." + ((Element) childNode).getAttribute("id"), Scene.v().getSootClass(parameterTypeProperty));
                    }
                    String id = ((Element) childNode).getAttribute("id");
                    Map<String, String> foreachMap;
                    if (mapperAndResultMap.containsKey(id)) {
                        foreachMap = mapperAndResultMap.get(id);
                    } else {
                        foreachMap = new HashMap<>();
                        mapperAndResultMap.put(id, foreachMap);
                    }
                    String textContent = formatSQL(resolveChildNode(childNode.getChildNodes(), sqlContent, foreachMap)) + ";";
                    String key = namespace + "." + id;
                    mapperAndSQL.put(key, textContent);
                    mapperCategory.put(key, nodeName.toLowerCase());
                    break;
                case "sql":
                    id = ((Element) childNode).getAttribute("id");
                    sqlContent.put(id, formatSQL(resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap)));
                    break;
                case "resultmap":
                    id = ((Element) childNode).getAttribute("id");
                    String type = ((Element) childNode).getAttribute("type");
                    resultMapProperties.put(id, type);
                    resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap);
                    break;
            }
        }
    }

    public String resolveChildNode(NodeList childNodeList, Map<String, String> sqlContent, Map<String, String> resultMap) {
        StringBuilder sqlStatement = new StringBuilder();
        for (int temp = 0; temp < childNodeList.getLength(); temp++) {
            Node childNode = childNodeList.item(temp);
            switch (childNode.getNodeName()) {
                case "include":
                    String refid = ((Element) childNode).getAttribute("refid");
                    if (sqlContent.containsKey(refid)) {
                        sqlStatement.append(sqlContent.get(refid));
                    }
                    break;
                case "#text":
                    sqlStatement.append(childNode.getTextContent());
                    break;
                case "trim":
                    sqlStatement.append(((Element) childNode).getAttribute("prefix"));
                    sqlStatement.append(resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap));
                    sqlStatement.append(((Element) childNode).getAttribute("suffix"));
                    break;
                case "set":
                    sqlStatement.append("set");
                    sqlStatement.append(resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap));
                    break;
                case "if":
                case "choose":
                case "when":
                    sqlStatement.append(resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap));
                    break;
                case "foreach":
                    String collection = ((Element) childNode).getAttribute("collection");
                    String item = ((Element) childNode).getAttribute("item");
                    resultMap.put(collection, item);
                    sqlStatement.append(resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap));
                    break;
                case "where":
                    sqlStatement.append(" where ");
                    sqlStatement.append(resolveChildNode(childNode.getChildNodes(), sqlContent, resultMap));
                    break;
                case "id":
                case "result":
                    String tableKey = ((Element) childNode).getAttribute("column");
                    String classKey = ((Element) childNode).getAttribute("property");
                    resultMap.put(tableKey, classKey);
            }
        }
        return sqlStatement.toString();
    }

    public void solve(DBMethodBean dbMethodBean, Map<String, String> resultMap) {
        String textContent = dbMethodBean.getMethodContext();
        Matcher matcher = pattern.matcher(textContent);
        while (matcher.find()) {
            DBColumnBean dbColumnBean = new DBColumnBean();
            dbColumnBean.setValueName(matcher.group().replaceAll("[${}]", ""));
            dbColumnBean.setParamIndex(0);
            dbMethodBean.addSQLParamName(dbColumnBean);
        }
        saveWhereCondition(dbMethodBean);
        switch (dbMethodBean.getMethodType()) {
            case "select":
                Matcher selectTableNameMatcher = selectTableNamePattern.matcher(textContent);
                Matcher selectValueMatcher = selectValuePattern.matcher(textContent);
                while (selectTableNameMatcher.find()) {
                    dbMethodBean.setTableName(selectTableNameMatcher.group(3).trim().replaceAll("[${}]", ""));
                    break;
                }
                while (selectValueMatcher.find()) {
                    for (String value : selectValueMatcher.group(4).split(",")) {
                        DBColumnBean dbColumnBean = new DBColumnBean();
                        String newValue = value.replace("(*)", "").replaceAll("[()_]", "").trim();
                        dbColumnBean.setParamName(newValue);
                        if (resultMap.containsKey(newValue)) {
                            dbColumnBean.setValueName(resultMap.get(newValue));
                        }
                        dbMethodBean.addDbColumnBean(dbColumnBean);
                    }
                    break;
                }
                break;
            case "update":
                Matcher updateTableNameMatcher = updateTableNamePattern.matcher(textContent);
                Matcher updateParamMatcher = updateParamPattern.matcher(textContent);
                while (updateTableNameMatcher.find()) {
                    dbMethodBean.setTableName(updateTableNameMatcher.group(2).trim());
                    break;
                }
                saveKeyAndValue(dbMethodBean, resultMap, updateParamMatcher);
                break;
            case "delete":
                Matcher deleteTableNameMatcher = deleteTableNamePattern.matcher(textContent);
                while (deleteTableNameMatcher.find()) {
                    dbMethodBean.setTableName(deleteTableNameMatcher.group(2).trim());
                    break;
                }
                break;
            case "insert":
                Matcher insertTableNameMatcher = insertTableNamePattern.matcher(textContent);
                Matcher insertParamMatcher = insertParamPattern.matcher(textContent);
                Matcher insertValueMatcher = insertValuePattern.matcher(textContent);
                Matcher insertValueAndParamMatcher = updateParamPattern.matcher(textContent);
                while (insertTableNameMatcher.find()) {
                    dbMethodBean.setTableName(insertTableNameMatcher.group(2).trim());
                    break;
                }
                while (insertParamMatcher.find()) {
                    for (String param : insertParamMatcher.group(2).split(",")) {
                        DBColumnBean dbColumnBean = new DBColumnBean();
                        dbColumnBean.setParamName(param.trim());
                        dbMethodBean.addDbColumnBean(dbColumnBean);
                    }
                }
                while (insertValueMatcher.find()) {
                    String[] valueArray = insertValueMatcher.group(2).split(",");
                    for (int i = 0; i < valueArray.length; i++) {
                        String valueName = valueArray[i].replaceAll("[(#${},_:]", "").trim();
                        DBColumnBean dbColumnBean;
                        if (dbMethodBean.getDbColumnBeanList().size() <= i) {
                            dbColumnBean = new DBColumnBean();
                            dbColumnBean.setParamName(valueName);
                            dbMethodBean.addDbColumnBean(dbColumnBean);
                        } else {
                            dbColumnBean = dbMethodBean.getDbColumnBeanList().get(i);
                        }
                        dbColumnBean.setValueName(valueName);
                        if (valueArray[i].contains("#") || valueArray[i].contains("$")) {
                            dbColumnBean.setParamIndex(0);
                        }
                        if (!resultMap.containsKey(dbColumnBean.getParamName())) {
                            resultMap.put(dbColumnBean.getParamName(), dbColumnBean.getValueName());
                        }
                    }
                }
                saveKeyAndValue(dbMethodBean, resultMap, insertValueAndParamMatcher);
                break;
        }
    }

    public void solveForMethodName(DBMethodBean dbMethodBean) {
        SootMethod mapperMethod = dbMethodBean.getSootMethod();
        String methodName = mapperMethod.getName();
        Type returnType = mapperMethod.getReturnType();
        if (!methodName.contains("By")) {
            return;
        }
        String[] splitString = methodName.split("By");
        if (splitString.length < 2) {
            return;
        }
        String conditions = splitString[1];
        if (splitString[0].startsWith("find") && !splitString[0].startsWith("findAll")) {
            String tableName = splitString[0].replace("find", "");
            dbMethodBean.setMethodType("select");
            if (tableName.equals("") && returnType instanceof RefType) {
                DBColumnBean dbColumnBean = new DBColumnBean();
                dbColumnBean.setValueName("*");
                dbColumnBean.setParamName("*");
                dbMethodBean.addDbColumnBean(dbColumnBean);
                tableName = findTableFromClass((RefType) returnType, tableName);
            }
            dbMethodBean.setTableName(tableName);
            findConditionFromMethodName(dbMethodBean, conditions);
        } else if (splitString[0].startsWith("findAll")) {
            String tableName = splitString[0].replace("findAll", "");
            dbMethodBean.setMethodType("select");
            if (tableName.equals("") && returnType instanceof RefType) {
                findRealClass(dbMethodBean, mapperMethod.getDeclaringClass(), mapperMethod);
                DBColumnBean dbColumnBean = new DBColumnBean();
                dbColumnBean.setValueName("*");
                dbColumnBean.setParamName("*");
                dbMethodBean.addDbColumnBean(dbColumnBean);
                if (dbMethodBean.getGenerics() != null) {
                    returnType = dbMethodBean.getGenerics().getType();
                }
                tableName = findTableFromClass((RefType) returnType, tableName);
            }
            dbMethodBean.setTableName(tableName);
            findConditionFromMethodName(dbMethodBean, conditions);
        } else if (splitString[0].startsWith("count")) {

        }
    }

    private String findTableFromClass(RefType returnType, String tableName) {
        SootClass modelClass = returnType.getSootClass();
        Tag visibilityAnnotationTag = modelClass.getTag("VisibilityAnnotationTag");
        if (visibilityAnnotationTag != null) {
            for (AnnotationTag annotation : ((VisibilityAnnotationTag) visibilityAnnotationTag).getAnnotations()) {
                String type = annotation.getType();
                if (type.equals("Ljavax/persistence/Table;")) {
                    for (AnnotationElem elem : annotation.getElems()) {
                        if (elem instanceof AnnotationStringElem) {
                            tableName = ((AnnotationStringElem) elem).getValue();
                            break;
                        }
                    }
                    break;
                }
            }
        }
        return tableName;
    }

    private void findConditionFromMethodName(DBMethodBean dbMethodBean, String conditions) {
        String[] conditionArray = conditions.split("And");
        for (int i = 0; i < conditionArray.length; i++) {
            String condition = conditionArray[i];
            if (!condition.equals("")) {
                DBColumnBean dbColumnBean = new DBColumnBean();
                dbColumnBean.setValueName(condition);
                dbColumnBean.setParamName(condition);
                dbColumnBean.setParamIndex(i);
                dbColumnBean.setLink("and");
                dbColumnBean.setRelation("=");
                dbMethodBean.addWhereCondition(dbColumnBean);
            }
        }
    }

    private void saveWhereCondition(DBMethodBean dbMethodBean) {
        Matcher whereMatcher = wherePattern.matcher(dbMethodBean.getMethodContext());
        while (whereMatcher.find()) {
            String whereConditions = whereMatcher.group(2);
            DBColumnBean dbColumnBean = null;
            for (String item : whereConditions.split(" ")) {
                if ((item.equalsIgnoreCase("and") || item.equalsIgnoreCase("or")) && dbColumnBean != null) {
                    dbColumnBean.setLink(item);
                } else if (item.contains("=")) {
                    dbColumnBean = new DBColumnBean();
                    dbColumnBean.setRelation("=");
                    String[] keyValue = item.split("=");
                    dbColumnBean.setParamName(keyValue[0]);
                    dbColumnBean.setValueName(keyValue[1]);
                    if (keyValue[1].contains("#") || keyValue[1].contains("$")) {
                        dbColumnBean.setParamIndex(0);
                    }
                    if (keyValue[1].startsWith("?")) {
                        dbColumnBean.setParamIndex(Integer.parseInt(keyValue[1].replaceAll("[?)]", "")));
                    }
                    dbMethodBean.addWhereCondition(dbColumnBean);
                } else if (item.contains("??")) {
                    dbColumnBean = new DBColumnBean();
                    dbColumnBean.setRelation("in");
                    String[] keyValue = item.split("\\?\\?");
                    dbColumnBean.setParamName(keyValue[0]);
                    dbColumnBean.setValueName(keyValue[1].replaceAll("[(#${}_:]", "").trim());
                    if (keyValue[1].contains("#") || keyValue[1].contains("$")) {
                        dbColumnBean.setParamIndex(0);
                    }
                    if (keyValue[1].startsWith("?")) {
                        dbColumnBean.setParamIndex(Integer.parseInt(keyValue[1].replace("?", "")));
                    }
                    dbMethodBean.addWhereCondition(dbColumnBean);
                }
            }
            break;
        }
    }

    private void saveKeyAndValue(DBMethodBean dbMethodBean, Map<String, String> resultMap, Matcher updateParamMatcher) {
        while (updateParamMatcher.find()) {
            for (String param : updateParamMatcher.group(2).split(",")) {
                String[] keyValue = param.trim().split("=");
                DBColumnBean dbColumnBean = new DBColumnBean();
                dbColumnBean.setParamName(keyValue[0].trim());
                Matcher caseMatcher = casePattern.matcher(keyValue[1]);
                while (caseMatcher.find()) {
                    keyValue[1] = caseMatcher.group(2);
                }
                dbColumnBean.setValueName(keyValue[1].replaceAll("[(#${}_]", "").trim());
                if (keyValue[1].contains("#") || keyValue[1].contains("$")) {
                    dbColumnBean.setParamIndex(0);
                }
                if (!resultMap.containsKey(dbColumnBean.getParamName())) {
                    resultMap.put(dbColumnBean.getParamName(), dbColumnBean.getValueName());
                }
                dbMethodBean.addDbColumnBean(dbColumnBean);
            }
        }
    }

    public void fillDBMethod(SootMethod method, DBMethodBean dbMethodBean) {
        List<String> paramTypes = new ArrayList<>();
        for (Type parameterType : method.getParameterTypes()) {
            paramTypes.add(parameterType.toString());
        }
        dbMethodBean.setParamNames(paramNameBind(method));

        String methodType = null;
        String methodContext = null;
        String providerClassName = null;
        String providerMethodName = null;
        Tag visibilityAnnotationTag = method.getTag("VisibilityAnnotationTag");
        if (visibilityAnnotationTag != null) {
            AnnotationTag annotationTag = null;
            AnnotationTag annotationTagProvider = null;
            for (AnnotationTag annotation : ((VisibilityAnnotationTag) visibilityAnnotationTag).getAnnotations()) {
                String type = annotation.getType();
                switch (type) {
                    case ANNOTATION_TYPE_MYBATIS_UPDATE_PROVIDER:
                        annotationTagProvider = annotation;
                    case ANNOTATION_TYPE_MYBATIS_UPDATE:
                        annotationTag = annotation;
                        methodType = "update";
                        break;
                    case ANNOTATION_TYPE_MYBATIS_DELETE_PROVIDER:
                        annotationTagProvider = annotation;
                    case ANNOTATION_TYPE_MYBATIS_DELETE:
                        annotationTag = annotation;
                        methodType = "delete";
                        break;
                    case ANNOTATION_TYPE_MYBATIS_SELECT_PROVIDER:
                        annotationTagProvider = annotation;
                    case ANNOTATION_TYPE_MYBATIS_SELECT:
                        annotationTag = annotation;
                        methodType = "select";
                        break;
                    case ANNOTATION_TYPE_MYBATIS_INSERT_PROVIDER:
                        annotationTagProvider = annotation;
                    case ANNOTATION_TYPE_MYBATIS_INSERT:
                        annotationTag = annotation;
                        methodType = "insert";
                        break;
                    case ANNOTATION_TYPE_JPA_QUERY:
                        annotationTag = annotation;
                        methodType = "";
                        break;
                }
            }
            if (annotationTag != null && annotationTagProvider == null) {
                Collection<AnnotationElem> elems = annotationTag.getElems();
                for (AnnotationElem elem : elems) {
                    if (elem instanceof AnnotationArrayElem) {
                        ArrayList<AnnotationElem> values = ((AnnotationArrayElem) elem).getValues();
                        for (AnnotationElem value : values) {
                            methodContext = ((AnnotationStringElem) value).getValue().trim();
                            if (!methodContext.toLowerCase().startsWith(methodType)) {
                                methodType = methodContext.split(" ")[0];
                            }
                        }
                    } else if (elem instanceof AnnotationStringElem) {
                        methodContext = ((AnnotationStringElem) elem).getValue();
                    }
                    if (methodType.equals("") && methodContext != null) {
                        methodType = methodContext.split(" ")[0].toLowerCase();
                    }
                }
            } else if (annotationTagProvider != null) {
                Collection<AnnotationElem> elems = annotationTagProvider.getElems();
                for (AnnotationElem elem : elems) {
                    if (elem instanceof AnnotationClassElem) {
                        String desc = ((AnnotationClassElem) elem).getDesc().substring(1);
                        providerClassName = desc.replace('/', '.').substring(0, desc.length() - 1);
                    }
                    if (elem instanceof AnnotationStringElem) {
                        providerMethodName = ((AnnotationStringElem) elem).getValue();
                    }
                }
            }
        }
        if (providerClassName != null && providerMethodName != null) {
            SootClass sootClassUnsafe = Scene.v().getSootClassUnsafe(providerClassName);
            SootMethod sootMethod = null;
            if (sootClassUnsafe != null) {
                sootMethod = sootClassUnsafe.getMethod(providerMethodName);
            }
            String sql = null;
            if (sootMethod != null) {
                switch (methodType) {
                    case "select":
                        sql = ProviderUtil.resolveSelectProvider(sootMethod);
                        break;
                    case "delete":
                        sql = ProviderUtil.resolveDeleteProvider(sootMethod);
                        break;
                    case "update":
                        sql = ProviderUtil.resolveUpdateProvider(sootMethod);
                        break;
                    case "insert":
                        sql = ProviderUtil.resolveInsertProvider(sootMethod);
                        break;
                    default:
                        break;
                }
            }
            if (sql != null) {
                methodContext = sql;
            }
        }
        if (methodContext == null) {
            methodContext = "";
        } else {
            methodContext = methodContext + ";";
        }
        dbMethodBean.setMethodContext(formatSQL(methodContext));
        dbMethodBean.setMethodType(methodType);
    }

    private Map<Integer, String> paramNameBind(SootMethod method) {
        Map<Integer, String> tmp = new HashMap<>();
        Tag tag = method.getTag("VisibilityParameterAnnotationTag");
        if (tag != null) {
            int index = 0;
            for (VisibilityAnnotationTag visibilityAnnotationTag : ((VisibilityParameterAnnotationTag) tag).getVisibilityAnnotations()) {
                if (visibilityAnnotationTag != null) {
                    for (AnnotationTag annotation : visibilityAnnotationTag.getAnnotations()) {
                        if (annotation != null && "Lorg/apache/ibatis/annotations/Param;".equals(annotation.getType())) {
                            for (AnnotationElem elem : annotation.getElems()) {
                                if (elem instanceof AnnotationStringElem) {
                                    AnnotationStringElem elemString = (AnnotationStringElem) elem;
                                    tmp.put(index, elemString.getValue());
                                }
                            }
                        }
                    }
                }
                index++;
            }
        }
        return tmp;
    }

    public String formatSQL(String oriSQL) {
        String sql = oriSQL.replace("\n", "");
        sql = sql.trim().replaceAll("\\s+", " ");
        sql = sql.replaceAll("\\s*,\\s*", ",");
        sql = sql.replaceAll("\\s*\\)\\s*", ") ");
        sql = sql.replaceAll("\\s*\\(\\s*", " (");
        sql = sql.replaceAll("\\s*=\\s*", "=");
        // sql = sql.replaceAll("\\s*\\{\\s*", "{");
        // sql = sql.replaceAll("\\s*}\\s*", "}");
        sql = sql.replaceAll(",jdbcType=(\\w*)", "");
        sql = sql.replaceAll("\\s+in\\s+", "??");
        sql = sql.replaceAll("\\s+IN\\s+", "??");
        return sql;
    }

    public void findRealClass(DBMethodBean dbMethodBean, SootClass mapperClass, SootMethod mapperMethod) {
        String classListType = "";
        String methodListType = "";
        SootClass realClass = null;
        for (Tag tag : mapperClass.getTags()) {
            if (tag instanceof SignatureTag) {
                classListType = ((SignatureTag) tag).getSignature();
                break;
            }
        }
        for (Tag tag : mapperMethod.getTags()) {
            if (tag instanceof SignatureTag) {
                methodListType = ((SignatureTag) tag).getSignature().split("\\)")[1];
                if (methodListType.length() < 5) {
                    methodListType = ((SignatureTag) tag).getSignature().split("\\)")[0];
                }
                break;
            }
        }

        Pattern pattern = Pattern.compile("<[A-Za-z/;]*>");
        Matcher methodTypeMatcher = pattern.matcher(methodListType);
        Matcher classTypeMatcher = pattern.matcher(classListType);
        while (methodTypeMatcher.find()) {
            String type = methodTypeMatcher.group(0).trim().replace("/", ".").replaceAll("[<;>]", "").substring(1);
            realClass = Scene.v().getSootClass(type);
            if ((realClass.isAbstract() || realClass.isInterface()) && dbMethodBean.getResultClass() != null) {
                realClass = dbMethodBean.getResultClass();
            }
            break;
        }
        if (realClass == null || realClass.getMethods().size() == 0) {
            while (classTypeMatcher.find()) {
                String genericsType = classTypeMatcher.group(0).split(";")[0];
                genericsType = genericsType.trim().replace("/", ".").replaceAll("[<;>]", "").substring(1);
                realClass = Scene.v().getSootClass(genericsType);
                break;
            }
        }
        dbMethodBean.setGenerics(realClass);
    }
}
