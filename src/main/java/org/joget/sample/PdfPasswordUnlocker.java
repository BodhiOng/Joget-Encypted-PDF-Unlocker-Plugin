package org.joget.sample;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plugin to unlock password-protected PDF files
 */
public class PdfPasswordUnlocker extends DefaultApplicationPlugin {
    private final static String MESSAGE_PATH = "messages/PdfPasswordUnlocker";

    @Override
    public String getName() {
        return "PDF Password Unlocker";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "A tool to remove password protection from PDF files";
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(getClassName(), "/properties/PdfPasswordUnlocker.json", null, true, MESSAGE_PATH);
    }

    @Override
    public String getLabel() {
        return "PDF Password Unlocker";
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        String formDefIdSourceFile = (String) properties.get("formDefIdSourceFile");
        String sourceFileFieldId = (String) properties.get("sourceFileFieldId");
        String formDefIdOutputFile = (String) properties.get("formDefIdOutputFile");
        String outputFileFieldId = (String) properties.get("outputFileFieldId");
        String filePassword = (String) properties.get("password");
        filePassword = AppUtil.processHashVariable(filePassword, null, null, null);
        String recordId;

        WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
        if (wfAssignment != null) {
            recordId = appService.getOriginProcessId(wfAssignment.getProcessId());
        } else {
            recordId = (String) properties.get("recordId");
        }

        Form loadForm;
        File srcFile;

        if (formDefIdSourceFile != null && formDefIdOutputFile != null) {
            try {
                FormData formData = new FormData();
                formData.setPrimaryKeyValue(recordId);
                loadForm = appService.viewDataForm(appDef.getId(), appDef.getVersion().toString(), formDefIdSourceFile, null, null, null, formData, null, null);

                Element el = FormUtil.findElement(sourceFileFieldId, loadForm, formData);
                String pdfFilePath = FormUtil.getElementPropertyValue(el, formData);
                srcFile = FileUtil.getFile(pdfFilePath, loadForm, recordId);

                String password = SecurityUtil.decrypt(filePassword);

                String filePaths = srcFile.getPath();
                List<String> fileList = getFilesList(filePaths);
                StringBuilder resultBuilder = new StringBuilder();
                FormRowSet frs = new FormRowSet();

                for (String filePath : fileList) {
                    File currentFile = new File(filePath.trim());
                    byte[] unlockedPdfContent = unlockPdf(currentFile, password);
                    String unlockedPdfFileName = writeUnlockedPdfFile(currentFile, appService, appDef, formDefIdOutputFile, recordId, unlockedPdfContent);
                    if (resultBuilder.length() > 0) {
                        resultBuilder.append(";");
                    }
                    resultBuilder.append(unlockedPdfFileName);
                }

                if (!fileList.isEmpty()) {
                    FormRow row = new FormRow();
                    row.put(outputFileFieldId, resultBuilder.toString());
                    frs.add(row);
                    appService.storeFormData(appDef.getAppId(), appDef.getVersion().toString(), formDefIdOutputFile, frs, recordId);
                }

            } catch (IOException ex) {
                LogUtil.error(getClassName(), ex, ex.getMessage());
            }
        }
        return null;
    }

    /**
     * Unlocks a password-protected PDF file
     * 
     * @param pdfFile The password-protected PDF file
     * @param password The password to unlock the PDF
     * @return Byte array of the unlocked PDF content
     * @throws IOException If there's an error processing the PDF
     */
    private byte[] unlockPdf(File pdfFile, String password) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile, password)) {
            // If the document is encrypted, save it without encryption
            if (document.isEncrypted()) {
                // Remove the password protection
                document.setAllSecurityToBeRemoved(true);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                document.save(outputStream);
                return outputStream.toByteArray();
            } else {
                // If the document is not encrypted, just return its content
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                document.save(outputStream);
                return outputStream.toByteArray();
            }
        }
    }

    /**
     * Writes the unlocked PDF content to a file
     */
    private String writeUnlockedPdfFile(File uploadedFile, AppService appService, AppDefinition appDef, String formDefIdOutputFile, String recordId, byte[] unlockedPdfContent) throws IOException {
        String fileNameWithoutExt = FilenameUtils.removeExtension(uploadedFile.getName());
        String fileName = fileNameWithoutExt + "_unlocked.pdf";
        String tableName = appService.getFormTableName(appDef, formDefIdOutputFile);
        String path = FileUtil.getUploadPath(tableName, recordId);
        File unlockedFile = new File(path, fileName);
        FileUtils.writeByteArrayToFile(unlockedFile, unlockedPdfContent);
        return fileName;
    }

    /**
     * Parses a semicolon-separated list of file paths
     */
    public List<String> getFilesList(String filePaths) {
        String[] fileArray = filePaths.split(";");
        List<String> fileList = new ArrayList<>();

        String directoryPath = "";
        for (String filePath : fileArray) {
            String fullPath;
            String trimmedPath = filePath.trim();
            int lastSeparatorIndex = trimmedPath.lastIndexOf(File.separator);
            if (lastSeparatorIndex != -1) {
                directoryPath = trimmedPath.substring(0, lastSeparatorIndex);
                String fileName = trimmedPath.substring(lastSeparatorIndex + 1);
                fullPath = directoryPath + File.separator + fileName;
            } else {
                fullPath = directoryPath + File.separator + trimmedPath;
            }
            fileList.add(fullPath);
        }
        return fileList;
    }
}
