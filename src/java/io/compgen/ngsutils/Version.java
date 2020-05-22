package io.compgen.ngsutils;

import java.io.IOException;

import io.compgen.cmdline.annotation.Command;
import io.compgen.cmdline.annotation.Exec;
import io.compgen.cmdline.exceptions.CommandArgumentException;
import io.compgen.cmdline.impl.AbstractOutputCommand;

@Command(name="version", desc="Show program version", category="help")
public class Version extends AbstractOutputCommand {
    
    @Exec
    public void exec() throws IOException, CommandArgumentException {
        System.out.println(NGSUtils.getVersion());
    }
}
