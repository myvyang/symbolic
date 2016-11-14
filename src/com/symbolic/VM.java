package com.symbolic;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.*;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.graph.UnitGraph;
import soot.util.NumberedString;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by mengyuan.ymy on 2016/11/11.
 */
public class VM {

    private static final String THIS = "____this____";

    private static Map<String, ValueProperty> staticFields = new HashMap<String, ValueProperty>();

    private static Set<String> loadedClass = new HashSet<String>();


    static {
        ValueProperty v = new ValueProperty();
        v.value = "| ";
        staticFields.put("pp", v);
    }

    private String getStaticFieldKey(SootFieldRef fieldRef) {
        return fieldRef.declaringClass().getName() + ":" + fieldRef.name();
    }

    private ValueProperty getThis(Map<JimpleLocal, ValueProperty> locals) {
        for (JimpleLocal local : locals.keySet()) {
            if (local.getName().equals(THIS)) {
                return locals.get(local);
            }
        }

        return null;
    }

    private void mustEqual(Object a, Object b) {
        if (!(a == null && b == null) && !a.equals(b)) {
            G.v().out.print(a);
            G.v().out.print(" not equals to ");
            G.v().out.println(b);

            try {
                throw new Exception();
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.exit(-1);
        }
    }

    private void mustAssignableFrom(String father, String child) {
        try{
            mustAssignableFrom(Class.forName(father), Class.forName(child));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void mustAssignableFrom(Class father, Class child) {
        if (!father.isAssignableFrom(child)) {
            G.v().out.print(father);
            G.v().out.print(" is not Assignable From to ");
            G.v().out.println(child);

            try {
                throw new Exception();
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.exit(-1);
        }
    }


    private ValueProperty dispatch(Object o, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        // FIXME: 简化处理，一些无所谓的表达式就不去计算了

        // 比较语句，返回true
        if (ConditionExpr.class.isAssignableFrom(o.getClass()) ||
                (o instanceof JInstanceOfExpr)) {
            ValueProperty property = new ValueProperty();
            property.value = true;
            property.trueType = BooleanType.v();

            return property;
        }

        // 算术语句，直接返回
        if (AbstractBinopExpr.class.isAssignableFrom(o.getClass())) {
            Value value = ((AbstractBinopExpr)o).getOp1();

            ValueProperty property = new ValueProperty();
            property.trueType = value.getType();
            property.value = dispatch(value, locals, params).value;

            return property;
        }


        // 对应synchronized语法的，忽略，不需要返回值
        if (MonitorStmt.class.isAssignableFrom(o.getClass())) {
            return null;
        }

        Class<?> clazz = o.getClass();
        while (!clazz.getName().equals("java.lang.Object")) {
            String className = clazz.getName();
            String prefix = null;
            String[] prepare = new String[]{
                    "soot.jimple.internal.",
                    "soot.jimple.",
                    "soot."
            };

            for (String s : prepare) {
                if (className.startsWith(s)) {
                    prefix = s;
                    break;
                }
            }

            mustEqual(prefix != null, true);

            String methodName = "visit" + className.substring(prefix.length());


            try {
                Method method = this.getClass().getDeclaredMethod(methodName, clazz, Map.class, List.class);
                return (ValueProperty)method.invoke(this, o, locals, params);
            } catch (NoSuchMethodException e) {
                // 这里不处理，看是否有父类处理了
            } catch (InvocationTargetException e) {
                e.getCause().printStackTrace();
                G.v().out.println("stoped");
                System.exit(-1);
            } catch (Exception e) {
                G.v().out.println(e.toString());
                G.v().out.println("stoped UNKNOWN");
                System.exit(-1);
            }

            clazz = clazz.getSuperclass();
        }

        G.v().out.print(this);
        G.v().out.println(" can't invoke visit" + o.getClass().getName());
        mustEqual("NEVER TOUCH", "BUT NOT");

        return null;
    }

    private void loadClass(SootClass clazz) {
        if (!loadedClass.contains(clazz.getName())) {
            loadedClass.add(clazz.getName());

            SootMethod clinitMethod = clazz.getMethodByNameUnsafe("<clinit>");
            if (clinitMethod != null)
                visitMethod(clinitMethod, null, new ArrayList<ValueProperty>());
        }
    }

    public ValueProperty visitJCastExpr(JCastExpr expr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
            ValueProperty ovalue = dispatch(expr.getOp(), locals, params);

            ValueProperty property = new ValueProperty();
            property.trueType = expr.getType();
            property.fields = ovalue.fields;
            property.value = ovalue.value;
            property.hasTaint = ovalue.hasTaint;

            return property;
    }

    public ValueProperty visitJimpleLocal(JimpleLocal local, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return locals.get(local);
    }

    public ValueProperty visitJReturnStmt(JReturnStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return dispatch(stmt.getOp(), locals, params);
    }

    public ValueProperty visitJReturnVoidStmt(JReturnVoidStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return null;
    }

    public ValueProperty visitJGotoStmt(JGotoStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return null;
    }

    public ValueProperty visitJThrowStmt(JThrowStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return null;
    }

    public ValueProperty visitJArrayRef(JArrayRef stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return new ValueProperty();
    }

    public ValueProperty visitJNewExpr(JNewExpr expr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.trueType = expr.getType();

        return property;
    }

    public ValueProperty visitJIfStmt(JIfStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        dispatch(stmt.getCondition(), locals, params);

        // TODO: 做分支判断，这里先直接忽略if语句
        return null;
    }

    public ValueProperty visitJNegExpr(JNegExpr expr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = - (int) dispatch(expr.getOp(), locals, params).value;
        property.trueType = IntType.v();

        return property;
    }

    public ValueProperty visitJLengthExpr(JLengthExpr lengthExpr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = 100; // TODO: 符号执行，这里用计算公式表示
        property.trueType = IntType.v();

        return property;
    }

    public ValueProperty visitNullConstant(NullConstant n, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = null;
        property.trueType = NullType.v();

        return property;
    }

    public ValueProperty visitClassConstant(ClassConstant clazz, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = clazz.value;
        property.trueType = RefType.v("java.lang.Class");

        return property;
    }

    public ValueProperty visitFloatConstant(FloatConstant f, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = f.value; // TODO: 符号执行，这里用计算公式表示
        property.trueType = FloatType.v();

        return property;
    }

    public ValueProperty visitLongConstant(LongConstant l, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = l.value;
        property.trueType = LongType.v();

        return property;
    }

    public ValueProperty visitDoubleConstant(DoubleConstant d, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = d.value; // TODO: 符号执行，这里用计算公式表示
        property.trueType = DoubleType.v();

        return property;
    }

    public ValueProperty visitIntConstant(IntConstant i, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = i.value; // TODO: 符号执行，这里用计算公式表示
        property.trueType = IntType.v();

        return property;
    }

    public ValueProperty visitStringConstant(StringConstant s, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();
        property.value = s.value;
        property.trueType = (RefType) s.getType();

        ValueProperty _value = new ValueProperty();
        _value.value = s.value.toCharArray();
        _value.trueType = ArrayType.v(CharType.v(), 1);


        ValueProperty _hash = new ValueProperty();
        _hash.value = s.value.hashCode();
        _hash.trueType = IntType.v();

        property.fields.put("value", _value);
        property.fields.put("hash", _hash);

        return property;
    }

    public ValueProperty visitJNewArrayExpr(JNewArrayExpr array, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty property = new ValueProperty();

        Value sizeValue = array.getSize();

        int size = (int) dispatch(sizeValue, locals, params).value;

        property.trueType = ArrayType.v(array.getBaseType(), 1);
        property.fields.put("0", null);

        return property;
    }

    private void updateField(Map fields, String key, Type type) {
        if (!fields.containsKey(key)) {
            ValueProperty property = new ValueProperty();

            if (PrimType.class.isAssignableFrom(type.getClass()))
                property.value = 0;
            else
                property.value = null;

            property.trueType = type;
            fields.put(key, property);
        }
    }

    public ValueProperty visitStaticFieldRef(StaticFieldRef staticFieldRef, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        SootFieldRef fieldRef = staticFieldRef.getFieldRef();
        mustEqual(fieldRef.isStatic(), true);

        loadClass(fieldRef.declaringClass());

        String key = getStaticFieldKey(fieldRef);

        updateField(staticFields, key, fieldRef.type());
        return staticFields.get(key);
    }

    public ValueProperty visitJInstanceFieldRef(JInstanceFieldRef instanceFieldRef, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        SootFieldRef fieldRef = instanceFieldRef.getFieldRef();

        mustEqual(fieldRef.isStatic(), false);

        ValueProperty thisValue = getThis(locals);
        mustAssignableFrom(fieldRef.declaringClass().getName(), ((RefType) thisValue.trueType).getClassName());

        updateField(thisValue.fields, fieldRef.name(), fieldRef.type());
        ValueProperty field = thisValue.fields.get(fieldRef.name());

        mustEqual(fieldRef.type(), field.trueType);

        return field;
    }

    private List<ValueProperty> prepareArgs(InvokeExpr expr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        List<ValueProperty> thisParams = new ArrayList<ValueProperty>();
        if (expr.getArgCount() > 0) {
            for (Value value : expr.getArgs()) {
                thisParams.add(dispatch(value, locals, params));
            }
        }

        return thisParams;
    }

    private SootMethod firstMatchMethod(SootClass clazz, NumberedString sign) {
        SootMethod method = null;

        while (clazz != null) {
            try {
                method = clazz.getMethod(sign);

                if (method != null)
                    break;
            } catch (RuntimeException e) {
            }

            clazz = clazz.getSuperclass();
        }

        if (method == null) {
            G.v().out.print(clazz);
            G.v().out.println(" can't find " + sign);
            System.exit(-1);
        }

        return method;
    }

    public ValueProperty visitAbstractInstanceInvokeExpr(AbstractInstanceInvokeExpr expr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        Value value = expr.getBaseBox().getValue();

        ValueProperty thisValue = dispatch(value, locals, params);
        NumberedString sign = expr.getMethodRef().getSubSignature();

        SootMethod method = null;
        if (expr instanceof JVirtualInvokeExpr || expr instanceof JInterfaceInvokeExpr)
            method = firstMatchMethod(((RefType)thisValue.trueType).getSootClass(), sign);
        else
            method = expr.getMethod();

        return visitMethod(method, thisValue, prepareArgs(expr, locals, params));
    }

    public ValueProperty visitJStaticInvokeExpr(JStaticInvokeExpr expr, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        SootMethod invokeMethod = expr.getMethod();
        return visitMethod(invokeMethod, null, prepareArgs(expr, locals, params));
    }

    public Object visitJInvokeStmt(JInvokeStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        return dispatch(stmt.getInvokeExpr(), locals, params);
    }

    public ValueProperty visitThisRef(ThisRef thisRef, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty thisValue = getThis(locals);

        mustEqual(thisValue != null, true);

        return thisValue;
    }

    public ValueProperty visitParameterRef(ParameterRef param, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        mustEqual(params.size() > param.getIndex(), true);

        return params.get(param.getIndex());
    }

    public Object visitJIdentityStmt(JIdentityStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        Value left = stmt.getLeftOp();
        Value right = stmt.getRightOp();

        mustEqual(left instanceof JimpleLocal, true);

        ValueProperty value = dispatch(right, locals, params);

        mustEqual(value == null, false);

        locals.put((JimpleLocal) left, value);

        return null;
    }

    public void visitJAssignStmt(JAssignStmt stmt, Map<JimpleLocal, ValueProperty> locals, List<ValueProperty> params) {
        ValueProperty rvalue = null;

        rvalue = dispatch(stmt.getRightOp(), locals, params);

        if (stmt.getLeftOp() instanceof JimpleLocal) {
            locals.put((JimpleLocal) stmt.getLeftOp(), rvalue);
        } else if (stmt.getLeftOp() instanceof JInstanceFieldRef){
            JInstanceFieldRef instanceFieldRef = (JInstanceFieldRef) stmt.getLeftOp();
            SootFieldRef fieldRef = instanceFieldRef.getFieldRef();

            mustEqual(fieldRef.isStatic(), false);

            ValueProperty thisValue = getThis(locals);
            mustAssignableFrom(((RefType) instanceFieldRef.getBase().getType()).getClassName(),
                    ((RefType) thisValue.trueType).getClassName());

            thisValue.fields.put(fieldRef.name(), rvalue);
        } else if (stmt.getLeftOp() instanceof StaticFieldRef) {
            SootFieldRef fieldRef = ((StaticFieldRef)stmt.getLeftOp()).getFieldRef();
            String key = getStaticFieldKey(fieldRef);

            mustEqual(fieldRef.isStatic(), true);

            staticFields.put(key, rvalue);
        } else if (stmt.getLeftOp() instanceof JArrayRef) {
            Value base = ((JArrayRef) stmt.getLeftOp()).getBase();
            int index = (int) dispatch(((JArrayRef) stmt.getLeftOp()).getIndex(), locals, params).value;
            if (base instanceof JimpleLocal) {
                locals.get(base).fields.put(String.valueOf(index), rvalue);
            } else {
                mustEqual("NEVER TOUCH", "BUT NOT");
            }
        } else {
            mustEqual("NEVER TOUCH", "BUT NOT");
        }
    }

    public ValueProperty visitMethod(SootMethod method, ValueProperty thisValue, List<ValueProperty> params) {

        // 排除掉异常类的调用
        try {
            Class<?> curClass = Class.forName(method.getDeclaringClass().getName());
            if (Exception.class.isAssignableFrom(curClass)) {
                return new ValueProperty();
            }
        } catch (Exception e) {
        }


        if (method.isNative()) {
            ValueProperty value = new ValueProperty();
            value.trueType = method.getReturnType();

            //if (value.trueType)

            // 这里需要更精细的操作，以保证后续有值

            return value;
        }

        // 排除Object的<init>方法，避免无意义的调用
        if (method.getDeclaringClass().getName().equals("java.lang.Object") &&
                method.getName().equals("<init>")) {
            ValueProperty property = new ValueProperty();
            property.trueType = RefType.v("java.lang.Object");

            return property;
        }

        // 插入的检查代码，直接忽略
        if (method.getDeclaringClass().getName().equals("java.lang.Class") &&
                method.getName().equals("desiredAssertionStatus")) {
            ValueProperty property = new ValueProperty();
            property.trueType = BooleanType.v();
            property.value = false;

            return property;
        }


        loadClass(method.getDeclaringClass());



        Body body = method.retrieveActiveBody();
        Map<JimpleLocal, ValueProperty> locals = new HashMap<JimpleLocal, ValueProperty>();
        ValueProperty retval = null;

        if (thisValue != null) {
            JimpleLocal thisLocal = new JimpleLocal(THIS, thisValue.trueType);
            locals.put(thisLocal, thisValue);
        }

        Iterator<Local> iter = body.getLocals().iterator();
        while (iter.hasNext()) {
            JimpleLocal local = (JimpleLocal) iter.next();
            locals.put(local, new ValueProperty());
        }


        System.out.println(staticFields.get("pp").value + method.getDeclaringClass().getName());
        System.out.println(staticFields.get("pp").value + method.getName());
        //System.out.println(body.toString());
        System.out.flush();



        UnitGraph graph = new ExceptionalUnitGraph(body);
        Iterator gIt = graph.iterator();
        while (gIt.hasNext()) {
            Unit u = (Unit) gIt.next();

            System.out.println(staticFields.get("pp").value + "  " + u.toString());
            staticFields.get("pp").value = "|  " + (String)staticFields.get("pp").value;

            ValueProperty o = dispatch(u, locals, params);

            staticFields.get("pp").value = ((String)staticFields.get("pp").value).substring(3);

            // TODO: Return 语句的处理，这里取最后的return，但是不一定能用
            if (u instanceof JReturnStmt) {
                retval = o;
            }
        }

        if (method.getReturnType() instanceof VoidType)
            return null;
        return retval;
    }

}
