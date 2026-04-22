package net.spartanb312.grunteon.back.worker;

import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.SessionExecutionGateway;
import net.spartanb312.grunteon.obfuscator.web.ProjectInspectionService;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.StartResult;

public interface WorkerGateway extends SessionExecutionGateway {

    ProjectMeta projectMeta(ObfuscationSession session, ObfuscationSession.ProjectScope scope);

    ProjectTree projectTree(ObfuscationSession session, ObfuscationSession.ProjectScope scope);

    ProjectSource projectSource(ObfuscationSession session, ObfuscationSession.ProjectScope scope, String className);
}
