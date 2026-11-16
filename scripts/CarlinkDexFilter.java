import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.rewriter.*;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction3rc;
import com.android.tools.smali.dexlib2.Opcode;
import java.util.List;

/** Deduplicate Guava and route SDK log output through the host's diagnostic opt-in. */
class CarlinkDexFilter {
    static final String LOG_GATE = "Lcom/shilapi/xcertplay/carlink/CarlinkSdkLog;";
    public static void main(String[] args) throws Exception {
        var removed = new HashSet<>(Files.readAllLines(Path.of(args[2])));
        var original = DexFileFactory.loadDexFile(new File(args[0]), Opcodes.forApi(28));
        var gated = new DexRewriter(new RewriterModule() {
            @Override public Rewriter<MethodReference> getMethodReferenceRewriter(Rewriters rewriters) {
                return reference -> {
                    if (reference.getDefiningClass().equals("Landroid/util/Log;") &&
                            List.of("v", "d", "i", "w", "e", "wtf", "println").contains(reference.getName())) {
                        return new ImmutableMethodReference(LOG_GATE, reference.getName(), reference.getParameterTypes(), reference.getReturnType());
                    }
                    return reference;
                };
            }
            @Override public Rewriter<Instruction> getInstructionRewriter(Rewriters rewriters) {
                var normal = new InstructionRewriter(rewriters);
                return instruction -> {
                    if (instruction instanceof ReferenceInstruction ri && ri.getReference() instanceof MethodReference method &&
                            method.getName().equals("printStackTrace") && method.getParameterTypes().isEmpty()) {
                        var target = new ImmutableMethodReference(LOG_GATE, "printStackTrace", List.of("Ljava/lang/Throwable;"), "V");
                        if (instruction instanceof FiveRegisterInstruction regs)
                            return new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, regs.getRegisterC(), 0, 0, 0, 0, target);
                        if (instruction instanceof RegisterRangeInstruction regs)
                            return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, regs.getStartRegister(), 1, target);
                        throw new IllegalStateException("Unsupported stack trace instruction: " + instruction.getOpcode());
                    }
                    if (instruction instanceof ReferenceInstruction ri && ri.getReference() instanceof MethodReference method &&
                            method.getDefiningClass().equals("Ljava/io/PrintStream;") && method.getName().equals("println") &&
                            method.getParameterTypes().equals(List.of("Ljava/lang/String;"))) {
                        var target = new ImmutableMethodReference(LOG_GATE, "println", List.of("Ljava/io/PrintStream;", "Ljava/lang/String;"), "V");
                        if (instruction instanceof FiveRegisterInstruction regs)
                            return new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 2, regs.getRegisterC(), regs.getRegisterD(), 0, 0, 0, target);
                        if (instruction instanceof RegisterRangeInstruction regs)
                            return new ImmutableInstruction3rc(Opcode.INVOKE_STATIC_RANGE, regs.getStartRegister(), 2, target);
                        throw new IllegalStateException("Unsupported SDK print instruction: " + instruction.getOpcode());
                    }
                    return normal.rewrite(instruction);
                };
            }
        }).getDexFileRewriter().rewrite(original);
        var pool = new DexPool(original.getOpcodes());
        for (ClassDef definition : gated.getClasses()) {
            if (!removed.contains(definition.getType())) pool.internClass(definition);
        }
        var output = new FileDataStore(new File(args[1]));
        try { pool.writeTo(output); } finally { output.close(); }
    }
}
