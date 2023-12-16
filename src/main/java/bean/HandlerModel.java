package bean;

import frontend.CoRParser;
import soot.SootClass;
import soot.SootMethod;
import transformer.HandlerEnum;
import transformer.HandlerModelEnum;
import utils.SootUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HandlerModel implements Comparable<HandlerModel> {
    private SootClass sootClass;
    private List<SootClass> superClassList = new ArrayList<>();
    public int order;
    private List<String> pointcutExpressions = new ArrayList<>();
    private List<String> pointcutExcludes = new ArrayList<>();
    private SootMethod sootMethod;
    private HandlerEnum annotation;
    private String roleString;
    private CoRParser coRParser;
    private HandlerModelEnum handlerModelEnum;
    private SootClass insertSiteClass;

    public HandlerModel(SootClass sootClass, int order, SootMethod sootMethod, CoRParser coRParser, HandlerModelEnum handlerModelEnum) {
        this.sootClass = sootClass;
        this.order = order;
        this.sootMethod = sootMethod;
        this.coRParser = coRParser;
        this.handlerModelEnum = handlerModelEnum;
        this.superClassList = SootUtil.getAllSuperClasses(sootClass);
    }

    public HandlerModel(SootClass sootClass, int order, CoRParser coRParser, HandlerModelEnum handlerModelEnum) {
        this.sootClass = sootClass;
        this.order = order;
        this.coRParser = coRParser;
        this.handlerModelEnum = handlerModelEnum;
        this.superClassList = SootUtil.getAllSuperClasses(sootClass);
    }

    public HandlerModel(CoRParser coRParser) {
        this.order = Integer.MAX_VALUE;
        this.coRParser = coRParser;
    }

    public SootClass getSootClass() {
        return sootClass;
    }

    public void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    public List<SootClass> getSuperClassList() {
        return superClassList;
    }

    public void setSuperClassList(List<SootClass> superClassList) {
        this.superClassList = superClassList;
    }

    public void addSuperClass(SootClass superClass) {
        this.superClassList.add(superClass);
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public void setPointcutExpressions(List<String> pointcutExpressions) {
        this.pointcutExpressions = pointcutExpressions;
    }

    public void addPointcutExpressions(String pointcutExpression) {
        if (!pointcutExpressions.contains(pointcutExpression)) {
            this.pointcutExpressions.add(pointcutExpression);
        }
    }

    public void addPointcutExpressions(List<String> pointcutExpressions) {
        for (String pointcutExpression : pointcutExpressions) {
            addPointcutExpressions(pointcutExpression);
        }
    }

    public List<String> getPointcutExpressions() {
        return pointcutExpressions;
    }

    public SootMethod getSootMethod() {
        return sootMethod;
    }

    public void setSootMethod(SootMethod sootMethod) {
        this.sootMethod = sootMethod;
    }

    public HandlerEnum getAnnotation() {
        return annotation;
    }

    public void setAnnotation(HandlerEnum annotation) {
        this.annotation = annotation;
    }

    public List<String> getPointcutExcludes() {
        return pointcutExcludes;
    }

    public void setPointcutExcludes(List<String> pointcutExcludes) {
        this.pointcutExcludes = pointcutExcludes;
    }

    public void addPointcutExclude(String pointcutExclude) {
        if (!this.pointcutExcludes.contains(pointcutExclude)) {
            this.pointcutExcludes.add(pointcutExclude);
        }
    }

    public void addPointcutExclude(List<String> pointcutExcludes) {
        for (String pointcutExclude : pointcutExcludes) {
            addPointcutExclude(pointcutExclude);
        }
    }

    public String getRoleString() {
        return roleString;
    }

    public void setRoleString(String roleString) {
        this.roleString = roleString;
    }

    public CoRParser getCoRParser() {
        return coRParser;
    }

    public void setCoRParser(CoRParser coRParser) {
        this.coRParser = coRParser;
    }

    public HandlerModelEnum getHandlerModelEnum() {
        return handlerModelEnum;
    }

    public void setHandlerModelEnum(HandlerModelEnum handlerModelEnum) {
        this.handlerModelEnum = handlerModelEnum;
    }

    public SootClass getInsertSiteClass() {
        return insertSiteClass;
    }

    public void setInsertSiteClass(SootClass insertSiteClass) {
        this.insertSiteClass = insertSiteClass;
    }

    @Override
    public int compareTo(HandlerModel o) {
        if (!this.getHandlerModelEnum().getValue().equals(o.getHandlerModelEnum().getValue())) {
            return this.getHandlerModelEnum().ordinal() - o.getHandlerModelEnum().ordinal();
        } else if (o.order > this.order) {
            return -1;
        } else if (o.order < this.order) {
            return 1;
        } else if (this.getSootClass().getShortName().compareTo(o.getSootClass().getShortName()) > 0) {
            return 1;
        } else if (this.getSootClass().getShortName().compareTo(o.getSootClass().getShortName()) < 0) {
            return -1;
        } else {
            return this.getAnnotation().ordinal() - o.getAnnotation().ordinal();
        }
    }


    @Override
    public String toString() {
        return "HandlerModel{" +
                "sootClass=" + sootClass +
                ", order=" + order +
                ", pointcutExpressions=" + pointcutExpressions +
                ", pointcutExcludes=" + pointcutExcludes +
                ", sootMethod=" + sootMethod +
                ", annotation=" + annotation +
                ", roleString='" + roleString + '\'' +
                ", handlerModelEnum=" + handlerModelEnum +
                ", insertSiteClass=" + insertSiteClass +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HandlerModel that = (HandlerModel) o;
        return order == that.order &&
                Objects.equals(sootClass, that.sootClass) &&
                Objects.equals(pointcutExpressions, that.pointcutExpressions) &&
                Objects.equals(sootMethod, that.sootMethod) &&
                annotation == that.annotation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sootClass, order, pointcutExpressions, sootMethod, annotation);
    }
}
