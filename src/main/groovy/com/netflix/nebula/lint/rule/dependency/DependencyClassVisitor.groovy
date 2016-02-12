package com.netflix.nebula.lint.rule.dependency

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.objectweb.asm.*
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.slf4j.Logger

class DependencyClassVisitor extends ClassVisitor {
    private Map<String, Set<ModuleVersionIdentifier>> classOwners
    private String className
    private Logger logger

    Set<ModuleVersionIdentifier> references = new HashSet()

    DependencyClassVisitor(Map<String, Set<ModuleVersionIdentifier>> classOwners, Logger logger) {
        super(Opcodes.ASM5)
        this.classOwners = classOwners
        this.logger = logger
    }

    void readSignature(String signature) {
        if(signature)
            new SignatureReader(signature).accept(new DependencySignatureVisitor())
    }

    void readObjectName(String type) {
        def owners = classOwners[Type.getObjectType(type).internalName] ?: Collections.emptySet()
        if(logger.isDebugEnabled()) {
            for (owner in owners) {
                logger.debug("$className refers to $type which was found in $owner")
            }
        }
        references.addAll(owners)
    }

    void readType(String desc) {
        if(!desc) return
        def t = Type.getType(desc)
        switch(t.sort) {
            case Type.ARRAY:
                readType(t.elementType.descriptor)
                break
            case Type.OBJECT:
                readObjectName(t.internalName)
                break
            default:
                readObjectName(desc)
        }
    }

    @Override
    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name
        readObjectName(superName)
        interfaces.each { readObjectName(it) }
        readSignature(signature)
    }

    @Override
    AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        readType(desc)
        return new DependencyAnnotationVisitor()
    }

    @Override
    AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        readType(desc)
        return new DependencyAnnotationVisitor()
    }

    @Override
    FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        readType(desc)
        readSignature(signature)
        return new DependencyFieldVisitor()
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        Type.getArgumentTypes(desc).each { readType(it.descriptor) }
        readType(Type.getReturnType(desc).descriptor)
        readSignature(signature)
        return new DependencyMethodVisitor()
    }

    class DependencySignatureVisitor extends SignatureVisitor {
        DependencySignatureVisitor() {
            super(Opcodes.ASM5)
        }

        @Override void visitClassType(String name) { readObjectName(name) }

        @Override SignatureVisitor visitInterfaceBound() { this }
        @Override SignatureVisitor visitClassBound() { this }
        @Override SignatureVisitor visitReturnType() { this }
        @Override SignatureVisitor visitParameterType() { this }
        @Override SignatureVisitor visitExceptionType() { this }
        @Override SignatureVisitor visitArrayType() { this }
    }

    class DependencyFieldVisitor extends FieldVisitor {
        DependencyFieldVisitor() {
            super(Opcodes.ASM5)
        }

        @Override
        AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }
    }

    class DependencyMethodVisitor extends MethodVisitor {
        DependencyMethodVisitor() {
            super(Opcodes.ASM5)
        }

        @Override
        void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            readType(Type.getReturnType(desc).descriptor)
            Type.getArgumentTypes(desc).collect { readType(it.descriptor) }
        }

        @Override
        void visitFieldInsn(int opcode, String owner, String name, String desc) {
            readType(desc)
        }

        @Override
        void visitTypeInsn(int opcode, String type) {
            readType(type)
        }

        @Override
        void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            readType(desc)
        }

        @Override
        AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            readType(type)
        }

        @Override
        AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            readType(desc)
            readSignature(signature)
        }

        @Override
        AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            readType(desc)
            return new DependencyAnnotationVisitor()
        }

        @Override
        void visitMultiANewArrayInsn(String desc, int dims) {
            readType(desc)
        }

        @Override
        void visitLdcInsn(Object cst) {
            if(cst instanceof Type) {
                readObjectName(cst.internalName)
            }
        }
    }

    class DependencyAnnotationVisitor extends AnnotationVisitor {
        DependencyAnnotationVisitor() {
            super(Opcodes.ASM5)
        }

        @Override
        void visit(String name, Object value) {
            if(value instanceof Type)
                readObjectName(value.internalName)
        }

        @Override
        void visitEnum(String name, String desc, String value) {
            readType(desc)
        }
    }
}
