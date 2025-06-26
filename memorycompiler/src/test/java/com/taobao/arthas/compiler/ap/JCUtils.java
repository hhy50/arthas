package com.taobao.arthas.compiler.ap;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import javax.lang.model.element.Modifier;
import java.util.Set;

public class JCUtils {


    /**
     * 获取所有字段
     *
     * @param jcClass 类的语法树节点
     * @return 字段的语法树节点的集合
     */
    public static List<JCTree.JCVariableDecl> getJClassFields(JCTree.JCClassDecl jcClass) {
        ListBuffer<JCTree.JCVariableDecl> jcVariables = new ListBuffer<>();
        for (JCTree jcTree : jcClass.defs) {
            if (isValidField(jcTree)) {
                jcVariables.append((JCTree.JCVariableDecl) jcTree);
            }
        }
        return jcVariables.toList();
    }


    /**
     * 判断是否是合法的字段
     *
     * @param jcTree 语法树节点
     * @return 是否是合法字段
     */
    private static boolean isValidField(JCTree jcTree) {
        if (jcTree.getKind().equals(JCTree.Kind.VARIABLE)) {
            JCTree.JCVariableDecl jcVariable = (JCTree.JCVariableDecl) jcTree;
            Set<Modifier> flagSets = jcVariable.mods.getFlags();
            return !flagSets.contains(Modifier.STATIC) && !flagSets.contains(Modifier.FINAL);
        }
        return false;
    }

    public static JCTree.JCMethodDecl createMethod(TreeMaker treeMaker, Name methodName, JCTree.JCExpression returnType, List<JCTree.JCVariableDecl> params,
                                                   List<JCTree.JCExpression> jcThrows, JCTree.JCBlock block, long modifier) {
        return treeMaker.MethodDef(
                treeMaker.Modifiers(modifier), //访问标志
                methodName, //名字
                returnType, //返回类型
                List.nil(), //泛型形参列表
                params, //参数列表
                jcThrows, //异常列表
                block, //方法体
                null); //默认方法（可能是interface中的那个default）
    }

    /**
     * 克隆一个字段的语法树节点，该节点作为方法的参数
     * 具有位置信息的语法树节点是不能复用的！
     *
     * @param treeMaker           语法树节点构造器
     * @param prototypeJCVariable 字段的语法树节点
     * @return 方法参数的语法树节点
     */
    public static JCTree.JCVariableDecl cloneJCVariableAsParam(TreeMaker treeMaker, JCTree.JCVariableDecl prototypeJCVariable) {
        return treeMaker.VarDef(
                treeMaker.Modifiers(Flags.PARAMETER), //访问标志
                prototypeJCVariable.name, //名字
                prototypeJCVariable.vartype, //类型
                null //初始化语句
        );
    }

    /**
     * 创建return语句
     *
     * @param treeMaker
     * @param obj
     * @param field
     * @param castType
     * @return
     */
    public static JCTree.JCReturn createReturn(TreeMaker treeMaker, JCTree.JCIdent obj, Name field, JCTree.JCExpression castType) {
        // 强转
        if (castType != null) {
            JCTree.JCExpression val = obj;
            if (field != null) {
                val = treeMaker.Select(obj, field);
            }
            return treeMaker.Return(
                    treeMaker.TypeCast(castType, val)
            );
        }

        if (field != null) {
            return treeMaker.Return(
                    treeMaker.Select(obj, field)
            );
        } else {
            return treeMaker.Return(obj);
        }
    }
}
