package net.spartanb312.grunteon.back.worker;

import kotlin.jvm.functions.Function0;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationService;
import net.spartanb312.grunteon.obfuscator.web.ObfuscationSession;
import net.spartanb312.grunteon.obfuscator.web.ProjectInspectionService;
import net.spartanb312.grunteon.obfuscator.web.ProjectMeta;
import net.spartanb312.grunteon.obfuscator.web.ProjectSource;
import net.spartanb312.grunteon.obfuscator.web.ProjectTree;
import net.spartanb312.grunteon.obfuscator.web.StartResult;
import net.spartanb312.grunteon.obfuscator.web.WebBridgeSupport;
import org.springframework.stereotype.Service;

@Service
public class LocalWorkerGateway implements WorkerGateway {

    private final ProjectInspectionService projectInspectionService;
    private final ObfuscationService obfuscationService;

    public LocalWorkerGateway(
        ProjectInspectionService projectInspectionService,
        ObfuscationService obfuscationService
    ) {
        this.projectInspectionService = projectInspectionService;
        this.obfuscationService = obfuscationService;
    }

    @Override
    public StartResult startSession(
        ObfuscationSession session,
        Function0<kotlin.Unit> onFinish,
        Function0<kotlin.Unit> onStart
    ) {
        return obfuscationService.start(
            session,
            WebBridgeSupport.buildExecutionConfig(session),
            onFinish,
            onStart
        );
    }

    @Override
    public ProjectMeta projectMeta(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        return projectInspectionService.projectMeta(session, scope);
    }

    @Override
    public ProjectTree projectTree(ObfuscationSession session, ObfuscationSession.ProjectScope scope) {
        return projectInspectionService.projectTree(session, scope);
    }

    @Override
    public ProjectSource projectSource(
        ObfuscationSession session,
        ObfuscationSession.ProjectScope scope,
        String className
    ) {
        return projectInspectionService.projectSource(session, scope, className);
    }
}
