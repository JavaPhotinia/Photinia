package backend;

import soot.*;
import soot.jimple.JimpleBody;

public interface MockObject {
    void mockJoinPoint(JimpleBody body, PatchingChain<Unit> units);

    Local mockBean(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass, SootMethod toCall);

    Local mockHttpServlet(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass);

    Local mockHttpSession(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass);

    Local mockFilterChain(JimpleBody body, PatchingChain<Unit> units, SootClass sootClass);
}
