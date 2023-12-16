package bean;

import soot.SootClass;
import soot.SootMethod;

import java.util.*;

public class DBMethodBean {
    private String methodName;
    private SootMethod sootMethod;
    private SootClass sootClass;
    private String tableName;
    private Map<Integer,String> paramNames = new HashMap<>();
    private List<DBColumnBean> dbColumnBeanList = new ArrayList<>();
    private int paramCount;
    private String methodType;
    private String methodContext;
    private List<DBColumnBean> sqlParamNames = new ArrayList<>();
    private String tableClassName;
    private SootClass Generics;
    private SootClass resultClass;

    private List<DBColumnBean> whereConditionList = new ArrayList<>();

    public DBMethodBean() {
    }

    @Override
    public String toString() {
        return "DBMethodBean{" +
                "methodName='" + methodName + '\'' +
                ", sootMethod=" + sootMethod +
                ", sootClass=" + sootClass +
                ", tableName='" + tableName + '\'' +
                ", paramNames=" + paramNames +
                ", dbColumnBeanList=" + dbColumnBeanList +
                ", paramCount=" + paramCount +
                ", methodType='" + methodType + '\'' +
                ", methodContext='" + methodContext + '\'' +
                ", sqlParamNames=" + sqlParamNames +
                ", tableClassName='" + tableClassName + '\'' +
                ", Generics=" + Generics +
                ", resultClass=" + resultClass +
                '}';
    }

    public String getMethodContext() {
        return methodContext;
    }

    public void setMethodContext(String methodContext) {
        this.methodContext = methodContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DBMethodBean)) return false;
        DBMethodBean that = (DBMethodBean) o;
        return Objects.equals(methodName, that.methodName) &&
                Objects.equals(paramCount, that.paramCount) &&
                Objects.equals(methodType, methodType) &&
                Objects.equals(sootClass.getName(), that.getSootClass().getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, paramCount,methodType);
    }

    public String getMethodType() {
        return methodType;
    }

    public void setMethodType(String methodType) {
        this.methodType = methodType;
    }

    public int getParamCount() {
        return paramCount;
    }

    public void setParamCount(int paramCount) {
        this.paramCount = paramCount;
    }

    public List<DBColumnBean> getDbColumnBeanList() {
        return dbColumnBeanList;
    }

    public void setDbColumnBeanList(List<DBColumnBean> dbColumnBeanList) {
        this.dbColumnBeanList = dbColumnBeanList;
    }

    public void addDbColumnBean(DBColumnBean dbColumnBean) {
        this.dbColumnBeanList.add(dbColumnBean);
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Map<Integer, String> getParamNames() {
        return paramNames;
    }

    public void setParamNames(Map<Integer, String> paramNames) {
        this.paramNames = paramNames;
    }

    public void addParamName(Integer index, String paramName) {
        this.paramNames.put(index, paramName);
    }

    public SootMethod getSootMethod() {
        return sootMethod;
    }

    public void setSootMethod(SootMethod sootMethod) {
        this.sootMethod = sootMethod;
    }

    public SootClass getSootClass() {
        return sootClass;
    }

    public void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    public List<DBColumnBean> getSqlParamNames() {
        return sqlParamNames;
    }

    public void setSqlParamNames(List<DBColumnBean> sqlParamNames) {
        this.sqlParamNames = sqlParamNames;
    }

    public void addSQLParamName(DBColumnBean sqlParamNames) {
        this.sqlParamNames.add(sqlParamNames);
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableClassName() {
        return tableClassName;
    }

    public void setTableClassName(String tableClassName) {
        this.tableClassName = tableClassName;
    }

    public SootClass getGenerics() {
        return Generics;
    }

    public void setGenerics(SootClass generics) {
        Generics = generics;
    }

    public SootClass getResultClass() {
        return resultClass;
    }

    public void setResultClass(SootClass resultClass) {
        this.resultClass = resultClass;
    }

    public List<DBColumnBean> getWhereConditionList() {
        return whereConditionList;
    }

    public void setWhereConditionList(List<DBColumnBean> whereConditionList) {
        this.whereConditionList = whereConditionList;
    }

    public void addWhereCondition(DBColumnBean dbColumnBean) {
        this.whereConditionList.add(dbColumnBean);
    }
}
