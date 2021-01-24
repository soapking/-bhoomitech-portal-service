package com.bhoomitech.portalservice.service;

import com.bhoomitech.portalservice.common.StatusCodes;
import com.bhoomitech.portalservice.model.FileStatus;
import com.bhoomitech.portalservice.model.Project;
import com.bhoomitech.portalservice.model.ProjectFileInfo;
import com.bhoomitech.portalservice.repository.ProjectRepository;
import com.bhoomitech.portalservice.util.ProjectConverter;
import com.xcodel.commons.common.ResponseObject;
import com.xcodel.commons.common.ResponseStatus;
import com.xcodel.commons.project.ProjectDocument;
import com.xcodel.commons.project.ProjectFileInfoDocument;
import com.xcodel.commons.project.ProjectFileType;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class ProjectService {

    public static final String AUTH_USER = "/auth/user/";
    private final ProjectRepository projectRepository;

    private final FileUploadService fileUploadService;

    public ProjectService(ProjectRepository projectRepository, FileUploadService fileUploadService) {
        this.projectRepository = projectRepository;
        this.fileUploadService = fileUploadService;
    }

    public List<Project> getProject() {
        return projectRepository.findAllByOrderByCreatedTimestampDesc();
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectDocument saveOrUpdateProject(@NonNull Project project) {
        return ProjectConverter.projectProjectDocumentFunction.apply(projectRepository.save(project), true);
    }

    public List<Project> getProjectByUserHref(String userHref) {
        return projectRepository.findAllByUserHrefOrderByCreatedTimestampDesc(userHref);
    }

    public ResponseObject checkProjectName(String projectName, String userHref) {
        ResponseObject responseObject = new ResponseObject();
        ResponseStatus responseStatus = new ResponseStatus();
        if (projectRepository.findByProjectNameAndUserHref(projectName, userHref).isPresent()) {
            responseObject.setResponseData("not available");
            responseStatus.setResultCode(StatusCodes.PROJECT_NAME_NOT_AVAILABLE.getStatusCode());
            responseStatus.setResultDescription(StatusCodes.PROJECT_NAME_NOT_AVAILABLE.getMessage());
        } else {
            responseStatus.setResultCode(StatusCodes.PROJECT_NAME_AVAILABLE.getStatusCode());
            responseStatus.setResultDescription(StatusCodes.PROJECT_NAME_AVAILABLE.getMessage());
            responseObject.setResponseData("available");
        }
        return responseObject;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createProjectFileInfo(
            String additionalDirStructure,
            String projectId,
            ProjectFileInfoDocument projectFileInfoDocument,
            ProjectFileType projectFileType,
            MultipartFile[] files) {
        Optional<Project> projectOptional = projectRepository.findById(Long.parseLong(projectId));
        if (projectOptional.isPresent()) {
            log.info("project name exists hence updating the info");
            Project project = projectOptional.get();
            if (project
                    .getFileInfos()
                    .stream()
                    .noneMatch(projectFileInfo -> projectFileInfo.getBasePointId().equals(projectFileInfoDocument.getBasePointId()))) {
                log.info("project is not having the same base point id");
                FileStatus fileStatus = this.fileUploadService.fileUpload(additionalDirStructure, files, project.getProjectName(), projectFileType);
                ProjectFileInfo projectFileInfo = ProjectConverter
                        .fileStatusDocumentProjectFileInfoDocumentProjectFileInfo
                        .apply(fileStatus, projectFileInfoDocument);
                project.getFileInfos().add(projectFileInfo);
                Project save = projectRepository.save(project);
                log.info("updated project {}", save);
                for (String fileName : fileStatus.getFileNames()) {
                    File deleteFile = new File(System.getProperty("user.dir") + "/" + fileName);
                    try {
                        log.info("deleting the uploaded file {} status {}", fileName, Files.deleteIfExists(deleteFile.toPath()));
                    } catch (IOException e) {
                        log.error("an error occurred while deleting a file", e);
                    }
                }

                projectFileInfoDocument.setMessage("successfully created");
            } else {
                projectFileInfoDocument.setMessage("cannot duplicate base point id in a single project");
            }
        } else {
            projectFileInfoDocument.setMessage("project not exists");
        }
    }

    public Project getProjectByProjectName(String projectName) {
        return projectRepository.findByProjectName(projectName).isPresent() ?
                projectRepository.findByProjectName(projectName).get() : new Project();
    }

    public boolean completeProject(@NonNull String projectId, @NonNull String success) {
        AtomicBoolean updated = new AtomicBoolean(false);
        Optional<Project> optionalProject = projectRepository.findById(Long.parseLong(projectId));
        optionalProject.ifPresent(project -> {
            if ("SUCCESS".equalsIgnoreCase(success)) {
                project.setStatus("SUBMITTED");
            } else if ("ERROR".equalsIgnoreCase(success)) {
                project.setStatus("ERROR");
            }
            projectRepository.save(project);
            updated.set(true);
        });
        return updated.get();
    }
}
