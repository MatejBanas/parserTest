import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.BreakStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntryStmt;
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter;
import com.github.javaparser.ast.visitor.ModifierVisitor;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Matej Banas
 */
public class parser {
    private static final String FILE_PATH = "src\\main\\resources\\Calculator.java";
    //private static final String FILE_PATH2 = "src\main\resources\\PM.java";
    //private static final String FILE_PATH3 = "src\main\resources\\PMC.java";
    private static List<Byte> constants = new ArrayList<Byte>();

    public static void main(String[] args) throws Exception {
        File file = new File(FILE_PATH);
        //File file2 = new File(FILE_PATH2);
        //File file3 = new File(FILE_PATH3);

        CompilationUnit compilationUnit = JavaParser.parse(file);
        // CompilationUnit compilationUnit2 = JavaParser.parse(file2);
        // CompilationUnit compilationUnit3 = JavaParser.parse(file3);

        getAllConstants(compilationUnit);
        System.out.println(getConstants());
        insertSwitchCaseStmnt(compilationUnit);
        insertTraps(compilationUnit);
        insertConstant(compilationUnit);

        //writeChanges(FILE_PATH, compilationUnit);

        System.out.println(compilationUnit);

    }

    /**
     * triggers a visitor that finds all constants and adds them to class attribute List constants
     * they can be used later for apdu triggers
     * visitor used: ConstantVisitor
     * ?
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void getAllConstants(CompilationUnit compilationUnit) {
        List<Byte> tmpConstants = new ArrayList<Byte>();
        compilationUnit.findAll(VariableDeclarator.class).stream()
                .filter(f -> f.getType().isPrimitiveType())
                .forEach(f -> tmpConstants.addAll(f.accept(new ConstantVisitor(), null)));
        setConstants(tmpConstants);
    }

    /**
     * triggers a visitor that adds traps to the beginning and end of every method in source file
     * visitor used: MethodAddPM
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void insertTraps(CompilationUnit compilationUnit) {
        compilationUnit.accept(new MethodAddPM(), null);
    }

    /**
     * triggers a visitor that adds case statement from PM.java file to switch statement in source file
     * visitor used: ProccesAddSwitch
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void insertSwitchCaseStmnt(CompilationUnit compilationUnit) {
        compilationUnit.accept(new ProccesAddSwitch(), null);
    }

    /**
     * adds INS_PERF_SETSTOP constant from PM to source class
     *
     * @param compilationUnit compilationUnit created by parsing source file
     */
    public static void insertConstant(CompilationUnit compilationUnit) {
        BodyDeclaration<?> declaration = JavaParser.parseAnnotationBodyDeclaration("public final static byte INS_PERF_SETSTOP = (byte) 0xf5;");
        compilationUnit.getTypes().get(0).getMembers().addFirst(declaration);
    }

    /**
     * writes changes to source file
     *
     * @param path            path to source file
     * @param compilationUnit compilationUnit created by parsing source file
     * @throws IOException exception
     */
    public static void writeChanges(String path, CompilationUnit compilationUnit) throws IOException {
        FileWriter fw = new FileWriter(path);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(compilationUnit.toString());
        bw.close();
        fw.close();
    }

    /**
     * changes package declaration of compilation unit b to package declaration from CU a
     *
     * @param a compilationUnit created by parsing source file
     * @param b compilationUnit created by parsing file of which we want to change the package declaration
     */
    public static void changePackage(CompilationUnit a, CompilationUnit b) {
        b.setPackageDeclaration(a.getPackageDeclaration().get());
    }

    public static void setConstants(List<Byte> constants) {
        parser.constants = constants;
    }

    public static List<Byte> getConstants() {
        return constants;
    }

    /**
     * visitor that gets all constants
     */
    private static class ConstantVisitor extends GenericListVisitorAdapter<Byte, Void> {
        List<Byte> bytes = new ArrayList<Byte>();

        @Override
        public List<Byte> visit(VariableDeclarator variableDeclarator, Void arg) {
            String name = variableDeclarator.getName().asString();
            if (variableDeclarator.getType().asString().equals("byte") && name.equals(name.toUpperCase())) {
                bytes.add((byte) variableDeclarator.getInitializer().get().asCastExpr().getExpression().asIntegerLiteralExpr().asInt());
                //variableDeclarator.getInitializer().get().ifIntegerLiteralExpr(f -> bytes.add((byte) f.asInt()));
            }
            return bytes;
        }
    }

    /**
     * visitor that adds traps
     */
    private static class MethodAddPM extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if (!methodDeclaration.getName().asString().matches("process") && !methodDeclaration.getName().asString().matches("install")) {
                boolean returnFound = false;
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                BlockStmt body = new BlockStmt();
                body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                for (int i = 0; i < statements.size(); i++) {
                    if (statements.get(i).isReturnStmt()) {
                        returnFound = true;
                        body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                    }
                    body.addStatement(statements.get(i));
                }
                if (!returnFound) {
                    body.addStatement("PM.check(PMC.TRAP_methodName_0);");
                }
                methodDeclaration.setBody(body);
            }
            return methodDeclaration;
        }
    }

    /**
     * visitor that adds case statement
     */
    private static class ProccesAddSwitch extends ModifierVisitor<Void> {
        @Override
        public MethodDeclaration visit(MethodDeclaration methodDeclaration, Void arg) {
            super.visit(methodDeclaration, arg);
            if (methodDeclaration.getName().asString().equals("process")) {
                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();
                BlockStmt body = new BlockStmt();
                NodeList<Statement> toBeInserted = new NodeList<Statement>();

                for (int i = 0; i < statements.size(); i++) {
                    /**
                     if(statements.get(i).getClass().equals(ExpressionStmt.class)){
                     System.out.println(statements.get(i).asExpressionStmt().getExpression().getClass());
                     }
                     */
                    if (statements.get(i).isSwitchStmt()) {
                        String name = statements.get(i).asSwitchStmt().getSelector().asArrayAccessExpr().getName().toString();
                        toBeInserted.add(JavaParser.parseStatement("PM.m_perfStop = Util.makeShort(" + name + "[ISO7816.OFFSET_CDATA], " + name + "[(short) (ISO7816.OFFSET_CDATA + 1)]);"));
                        toBeInserted.add(new BreakStmt().removeLabel());
                        NodeList<SwitchEntryStmt> entryStmts = statements.get(i).asSwitchStmt().getEntries();
                        entryStmts.addBefore(new SwitchEntryStmt(new NameExpr("INS_PERF_SETSTOP"), toBeInserted), statements.get(i).asSwitchStmt().getEntry(entryStmts.size() - 1));
                        statements.get(i).asSwitchStmt().setEntries(entryStmts);
                    }
                    body.addStatement(statements.get(i));
                }
                methodDeclaration.setBody(body);
            }
            return methodDeclaration;
        }
    }
}