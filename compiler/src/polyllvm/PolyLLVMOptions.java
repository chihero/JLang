package polyllvm;

import polyglot.ext.jl5.JL5Options;
import polyglot.main.OptFlag;
import polyglot.main.UsageError;

import java.util.Set;

public class PolyLLVMOptions extends JL5Options {

    public String entryPointClass;
    public boolean printDesugar;

    public PolyLLVMOptions(PolyLLVMExtensionInfo extension) {
        super(extension);
        this.setOutputExtension("ll");
    }

    @Override
    protected void populateFlags(Set<OptFlag<?>> flags) {
        //Add the ox flag before the super call to ensure the correct default ("ll") is used.
        flags.add(new OptFlag<String>("-ox", "<ext>", "set output extension") {
            @Override
            public Arg<String> handle(String[] args, int index) {
                return createArg(index + 1, args[index]);
            }

            @Override
            public Arg<String> defaultArg() {
                return createDefault("ll");
            }
        });

        super.populateFlags(flags);
        flags.add(new OptFlag<String>(
                "-entry-point", "<classname>",
                "Fully qualified main class to call") {
            @Override
            public Arg<String> handle(String[] args, int index) {
                return createArg(index + 1, args[index]);
            }

            @Override
            public Arg<String> defaultArg() {
                return createDefault(this.defaultValue);
            }
        });

        flags.add(new OptFlag.Switch(
                "-dump-desugared", "Print the desugared AST to stderr", true));
    }

    @Override
    protected void handleArg(OptFlag.Arg<?> arg) throws UsageError {
        if (arg.flag().ids().contains("-entry-point")) {
            this.entryPointClass = (String) arg.value();
        }
        else if (arg.flag().ids().contains("-dump-desugared")) {
            this.printDesugar = (Boolean) arg.value();
        }
        else super.handleArg(arg);
    }

}
