package polyllvm.extension;

import polyglot.ast.Binary;
import polyglot.ast.Binary.*;
import polyglot.ast.Expr;
import polyglot.ast.Node;
import polyglot.ast.NodeFactory;
import polyglot.types.Type;
import polyglot.types.TypeSystem;
import polyglot.util.CollectionUtil;
import polyglot.util.InternalCompilerError;
import polyglot.util.Position;
import polyglot.util.SerialVersionUID;
import polyllvm.ast.PolyLLVMExt;
import polyllvm.ast.PolyLLVMNodeFactory;
import polyllvm.ast.PseudoLLVM.Expressions.LLVMLabel;
import polyllvm.ast.PseudoLLVM.Expressions.LLVMOperand;
import polyllvm.ast.PseudoLLVM.Expressions.LLVMTypedOperand;
import polyllvm.ast.PseudoLLVM.LLVMTypes.LLVMTypeNode;
import polyllvm.ast.PseudoLLVM.Statements.LLVMInstruction;
import polyllvm.util.LLVMUtils;
import polyllvm.util.PolyLLVMFreshGen;
import polyllvm.util.PolyLLVMStringUtils;
import polyllvm.visit.AddPrimitiveWideningCastsVisitor;
import polyllvm.visit.PseudoLLVMTranslator;
import polyllvm.visit.StringLiteralRemover;

import java.util.List;

import static org.bytedeco.javacpp.LLVM.*;
import static polyglot.ast.Binary.*;

public class PolyLLVMBinaryExt extends PolyLLVMExt {
    private static final long serialVersionUID = SerialVersionUID.generate();

    @Override
    public Node removeStringLiterals(StringLiteralRemover v) {
        Binary n = (Binary) node();
        NodeFactory nf = v.nodeFactory();
        TypeSystem ts = v.typeSystem();
        if (n.left().type().isSubtype(ts.String()) && !n.left().type().isNull()
                || n.right().type().isSubtype(ts.String())
                        && !n.right().type().isNull()) {
            Expr left = n.left();
            Expr right = n.right();
            if (left.toString().equals("null")) {
                left = (Expr) PolyLLVMStringUtils.stringToConstructor(nf.StringLit(Position.compilerGenerated(),
                                                                                   left.toString()),
                                                                      nf,
                                                                      ts);
            }
            else if (!n.left().type().isSubtype(ts.String())) {
                left = nf.Call(left.position(),
                               nf.Id(Position.compilerGenerated(),
                                     "java.lang.String.valueOf"),
                               left)
                         .type(ts.String());
            }
            if (right.toString().equals("null")) {
                right = (Expr) PolyLLVMStringUtils.stringToConstructor(nf.StringLit(Position.compilerGenerated(),
                                                                                    right.toString()),
                                                                       nf,
                                                                       ts);
            }
            else if (!n.right().type().isSubtype(ts.String())) {
                right = nf.Call(right.position(),
                                nf.Id(Position.compilerGenerated(),
                                      "java.lang.String.valueOf"),
                                right)
                          .type(ts.String());
            }

            return nf.Call(n.position(),
                           left,
                           nf.Id(Position.compilerGenerated(), "concat"),
                           right)
                     .type(ts.String());
        }

        return super.removeStringLiterals(v);
    }

    @Override
    public Node addPrimitiveWideningCasts(AddPrimitiveWideningCastsVisitor v) {
        Binary n = (Binary) node();
        PolyLLVMNodeFactory nf = v.nodeFactory();
        Expr left = n.left();
        Expr right = n.right();

        Operator op = n.operator();
        // Rules for Binary Numeric Promotion found in Java Lang. Spec 5.6.2
        // All binary operands except for shifts, or, and are subject to the rules
        if (!(op == Binary.SHL || op == Binary.SHR || op == Binary.USHR
                || op == Binary.COND_OR || op == Binary.COND_AND)
                && left.type().isPrimitive() && right.type().isPrimitive()) {
            //"If either operand is of type double, the other is converted to double."
            if (left.type().isDouble() && !right.type().isDouble()) {
                right = nf.Cast(Position.compilerGenerated(),
                                nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                     left.type()),
                                right)
                          .type(left.type());
            }
            else if (!left.type().isDouble() && right.type().isDouble()) {
                left = nf.Cast(Position.compilerGenerated(),
                               nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                    right.type()),
                               left)
                         .type(right.type());
            }
            else if (left.type().isDouble() && right.type().isDouble()) {
                //Both are doubles -- do nothing
            }
            //Otherwise, if either operand is of type float, the other is converted to float.
            else if (left.type().isFloat() && !right.type().isFloat()) {
                right = nf.Cast(Position.compilerGenerated(),
                                nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                     left.type()),
                                right)
                          .type(left.type());
            }
            else if (!left.type().isFloat() && right.type().isFloat()) {
                left = nf.Cast(Position.compilerGenerated(),
                               nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                    right.type()),
                               left)
                         .type(right.type());
            }
            else if (left.type().isFloat() && right.type().isFloat()) {
                //Both are floats -- do nothing

            }
            //Otherwise, if either operand is of type long, the other is converted to long
            else if (left.type().isLong() && !right.type().isLong()) {
                right = nf.Cast(Position.compilerGenerated(),
                                nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                     left.type()),
                                right)
                          .type(left.type());
            }
            else if (!left.type().isLong() && right.type().isLong()) {
                left = nf.Cast(Position.compilerGenerated(),
                               nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                    right.type()),
                               left)
                         .type(right.type());
            }
            else if (left.type().isLong() && right.type().isLong()) {
                //Both are longs -- do nothing

            }
            //Otherwise, both operands are converted to type int
            else if (!left.type().isInt() && right.type().isInt()) {
                left = nf.Cast(Position.compilerGenerated(),
                               nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                    right.type()),
                               left)
                         .type(right.type());

            }
            else if (left.type().isInt() && !right.type().isInt()) {
                right = nf.Cast(Position.compilerGenerated(),
                                nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                     left.type()),
                                right)
                          .type(left.type());
            }
            else if (left.type().isInt() && right.type().isInt()) {
                //Do nothing: they are both already ints
            }
            else {
                TypeSystem ts = v.typeSystem();
                left = nf.Cast(Position.compilerGenerated(),
                               nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                    ts.Int()),
                               left)
                         .type(ts.Int());
                right = nf.Cast(Position.compilerGenerated(),
                                nf.CanonicalTypeNode(Position.compilerGenerated(),
                                                     ts.Int()),
                                right)
                          .type(ts.Int());
            }
            n = n.left(left);
            n = n.right(right);
            return n;
        }

        return super.addPrimitiveWideningCasts(v);
    }

    private static boolean isUnsigned(Type t) {
        return t.isChar();
    }

    private static int llvmIntBinopCode(Operator op, Type type) {
        if      (op == ADD)     return LLVMAdd;
        else if (op == SUB)     return LLVMSub;
        else if (op == MUL)     return LLVMMul;
        else if (op == DIV)     return isUnsigned(type) ? LLVMUDiv : LLVMSDiv;
        else if (op == MOD)     return isUnsigned(type) ? LLVMURem : LLVMSRem;
        else if (op == BIT_OR)  return LLVMOr;
        else if (op == BIT_AND) return LLVMAnd;
        else if (op == BIT_XOR) return LLVMXor;
        else if (op == SHL)     return LLVMShl;
        else if (op == USHR)    return LLVMLShr;
        else if (op == SHR)     return isUnsigned(type) ? LLVMLShr : LLVMAShr;
        else throw new InternalCompilerError("Invalid integer operation");
    }

    private static int llvmFloatBinopCode(Operator op) {
        if      (op == ADD) return LLVMFAdd;
        else if (op == SUB) return LLVMFSub;
        else if (op == MUL) return LLVMFMul;
        else if (op == DIV) return LLVMFDiv;
        else throw new InternalCompilerError("Invalid floating point operation");
    }

    private static int llvmICmpBinopCode(Operator op, Type t) {
        if      (op == LT) return isUnsigned(t) ? LLVMIntULT : LLVMIntSLT;
        else if (op == LE) return isUnsigned(t) ? LLVMIntULE : LLVMIntSLE;
        else if (op == EQ) return LLVMIntEQ;
        else if (op == NE) return LLVMIntNE;
        else if (op == GE) return isUnsigned(t) ? LLVMIntUGE : LLVMIntSGE;
        else if (op == GT) return isUnsigned(t) ? LLVMIntUGT : LLVMIntSGT;
        else throw new InternalCompilerError("This operation is not a comparison");
    }

    private static int llvmFCmpBinopCode(Operator op) {
        // Java floating point uses ordered comparisons (i.e., comparisons with NaN return false).
        if      (op == LT) return LLVMRealOLT;
        else if (op == LE) return LLVMRealOLE;
        else if (op == EQ) return LLVMRealOEQ;
        else if (op == NE) return LLVMRealONE;
        else if (op == GE) return LLVMRealOGE;
        else if (op == GT) return LLVMRealOGT;
        else throw new InternalCompilerError("This operation is not a comparison");
    }

    @Override
    public Node translatePseudoLLVM(PseudoLLVMTranslator v) {
        Binary n = (Binary) node();
        Type resType = n.type();
        LLVMValueRef left = v.getTranslation(n.left());
        LLVMValueRef right = v.getTranslation(n.right());
        Operator op = n.operator();

        // TODO: Will need to add widening casts here.
        assert(n.left().type().equals(n.right().type()));
        Type elemType = n.left().type();

        LLVMValueRef res;
        if (resType.isLongOrLess()) {
            // Integer binop.
            res = LLVMBuildBinOp(v.builder, llvmIntBinopCode(op, elemType), left, right, "binop");
        } else if (resType.isFloat() || resType.isDouble()) {
            // Floating point binop.
            res = LLVMBuildBinOp(v.builder, llvmFloatBinopCode(op), left, right, "binop");
        } else if (resType.isBoolean() && elemType.isLongOrLess()) {
            // Integer comparison.
            res = LLVMBuildICmp(v.builder, llvmICmpBinopCode(op, elemType), left, right, "cmp");
        } else if (resType.isBoolean() && (elemType.isFloat() || elemType.isDouble())) {
            // Floating point comparison.
            res = LLVMBuildFCmp(v.builder, llvmFCmpBinopCode(op), left, right, "cmp");
        } else {
            throw new InternalCompilerError("Invalid binary operation result type");
        }

        v.addTranslation(n, res);
        return super.translatePseudoLLVM(v);
    }

    @Override
    public Node translatePseudoLLVMConditional(PseudoLLVMTranslator v,
                                               LLVMLabel trueLabel, LLVMLabel falseLabel) {
        Binary n = (Binary) node();
        PolyLLVMNodeFactory nf = v.nodeFactory();
        Operator op = n.operator();
        if (op == Binary.COND_AND ){
            LLVMLabel l1 = PolyLLVMFreshGen.freshLabel(v.nodeFactory());
            List<LLVMInstruction> l = CollectionUtil.list(
                    (LLVMInstruction) lang().translatePseudoLLVMConditional(n.left(), v, l1, falseLabel),
                    nf.LLVMSeqLabel(l1),
                    (LLVMInstruction) lang().translatePseudoLLVMConditional(n.right(), v, trueLabel,falseLabel)
            );
            return nf.LLVMSeq(l);
        } else if (op == Binary.COND_OR) {
            LLVMLabel l1 = PolyLLVMFreshGen.freshLabel(v.nodeFactory());
            List<LLVMInstruction> l = CollectionUtil.list(
                    (LLVMInstruction) lang().translatePseudoLLVMConditional(n.left(), v, trueLabel, l1),
                    nf.LLVMSeqLabel(l1),
                    (LLVMInstruction) lang().translatePseudoLLVMConditional(n.right(), v, trueLabel,falseLabel)
            );
            return nf.LLVMSeq(l);
        }

        LLVMOperand translation = v.getTranslation(n);
        LLVMTypeNode tn = LLVMUtils.polyLLVMTypeNode(nf, n.type());
        LLVMTypedOperand cond = nf.LLVMTypedOperand(translation, tn);
        return  nf.LLVMBr(cond, trueLabel, falseLabel);
    }

    @Override
    public void translateLLVMConditional(PseudoLLVMTranslator v, LLVMBasicBlockRef trueBlock, LLVMBasicBlockRef falseBlock) {
        Binary n = (Binary) node();
        Operator op = n.operator();
        if (op == Binary.COND_AND) {
            LLVMBasicBlockRef initial = v.currentBlock;
            LLVMBasicBlockRef l1 = LLVMAppendBasicBlock(v.currFn(), "l1");
            LLVMPositionBuilderAtEnd(v.builder, v.currentBlock);
            lang().translateLLVMConditional(n.left(), v, l1, falseBlock);
            LLVMPositionBuilderAtEnd(v.builder, l1);
            lang().translateLLVMConditional(n, v, trueBlock, falseBlock);
            v.currentBlock = initial;
        } else if (op == Binary.COND_OR) {
            LLVMBasicBlockRef initial = v.currentBlock;
            LLVMBasicBlockRef l1 = LLVMAppendBasicBlock(v.currFn(), "l1");
            LLVMPositionBuilderAtEnd(v.builder, v.currentBlock);
            lang().translateLLVMConditional(n.left(), v, trueBlock, l1);
            LLVMPositionBuilderAtEnd(v.builder, l1);
            lang().translateLLVMConditional(n, v, trueBlock, falseBlock);
            v.currentBlock = initial;
        }

        LLVMValueRef val = v.getTranslation(n);
        LLVMBuildCondBr(v.builder, val, trueBlock, falseBlock);
    }
}
