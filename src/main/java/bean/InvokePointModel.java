package bean;

import soot.SootMethod;

import java.util.ArrayList;
import java.util.List;


public class InvokePointModel {
    public InvokePointModel(List<Integer> invokePointList, List<SootMethod> candidateSootMethod) {
        this.invokePointList = invokePointList;
        this.candidateSootMethod = candidateSootMethod;
    }

    List<Integer> invokePointList = new ArrayList<>();
    List<SootMethod> candidateSootMethod = new ArrayList<>();

    public List<Integer> getInvokePointList() {
        return invokePointList;
    }

    public void setInvokePointList(List<Integer> invokePointList) {
        this.invokePointList = invokePointList;
    }

    public List<SootMethod> getCandidateSootMethod() {
        return candidateSootMethod;
    }

    public void setCandidateSootMethod(List<SootMethod> candidateSootMethod) {
        this.candidateSootMethod = candidateSootMethod;
    }
}
