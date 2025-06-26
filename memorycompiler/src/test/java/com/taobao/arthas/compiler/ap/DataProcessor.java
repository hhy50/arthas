package com.taobao.arthas.compiler.ap;

import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;
import com.taobao.arthas.compiler.annotation.Data;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

import static com.sun.tools.javac.util.List.of;
import static com.taobao.arthas.compiler.ap.JCUtils.*;

@SupportedAnnotationTypes("com.taobao.arthas.compiler.annotation.Data")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DataProcessor extends AbstractProcessor {

    /**
     * 类的语法树节点
     */
    private JCTree.JCClassDecl jcClass;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;

    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.trees = JavacTrees.instance(processingEnv);
        this.names = Names.instance(context);
    }


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element ele : roundEnv.getElementsAnnotatedWith(Data.class)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "1111111111111111111111", ele);
            doProcess(ele);
        }
        return true;
    }

    public void doProcess(Element element) {
        JCTree jcTree = trees.getTree(element);
        jcTree.accept(new TreeTranslator() {
            @Override
            public void visitClassDef(JCTree.JCClassDecl jcClass) {
                treeMaker.at(jcClass.pos);
                jcClass.defs = jcClass.defs.appendList(
                        generateGetterAndSetter(jcClass)
                );
            }
        });
    }

    /**
     * 生成get/set方法
     *
     * @return get/set方法的语法树节点集合
     */
    private List<JCTree> generateGetterAndSetter(JCTree.JCClassDecl jcClass) {
        ListBuffer<JCTree> dataMethods = new ListBuffer<>();
        for (JCTree.JCVariableDecl field : getJClassFields(jcClass)) {
            dataMethods.append(generateGetter(field));
            dataMethods.append(generateSetter(field));
        }
        return dataMethods.toList();
    }

    /**
     * 创建set方法
     */
    private JCTree.JCMethodDecl generateSetter(JCTree.JCVariableDecl jcVariable) {
        // 添加语句 "this.xxx = xxx;"
        JCTree.JCBlock jcBlock = treeMaker.Block(0L,
                of(treeMaker.Exec(treeMaker.Assign(
                        treeMaker.Select(treeMaker.Ident(names.fromString("this")), jcVariable.name), treeMaker.Ident(jcVariable.name))
                )));
        return createMethod(
                treeMaker,
                names.fromString(firstCharUpper(jcVariable.name.toString())),
                treeMaker.TypeIdent(TypeTag.VOID),
                List.of(cloneJCVariableAsParam(treeMaker, jcVariable)),
                List.nil(),
                jcBlock,
                Flags.PUBLIC
        );
    }

    /**
     * 创建get方法
     *
     * @param jcVariable 字段的语法树节点
     * @return get方法的语法树节点
     */
    private JCTree.JCMethodDecl generateGetter(JCTree.JCVariableDecl jcVariable) {
        // 添加语句 " return this.xxx; "
        JCTree.JCBlock jcBlock = treeMaker.Block(0L,
                of(createReturn(treeMaker,
                        treeMaker.Ident(names.fromString("this")), jcVariable.name, null))
        );
        return createMethod(
                treeMaker,
                names.fromString("get"+firstCharUpper(jcVariable.name.toString())),
                jcVariable.vartype,
                List.nil(),
                List.nil(),
                jcBlock,
                Flags.PUBLIC
        );
    }


    public static String firstCharUpper(String fieldName) {
        char[] charArray = fieldName.toCharArray();
        char ch = charArray[0];
        charArray[0] = (char) (ch & 0xDF);
        return new String(charArray);
    }
}
