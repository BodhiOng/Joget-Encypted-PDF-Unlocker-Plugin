package org.joget.sample;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppPluginUtil;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.workflow.model.WorkflowAssignment;
import org.springframework.context.ApplicationContext;

/**
 * Plugin to unlock password-protected PDF files
 */
public class PdfPasswordUnlocker extends DefaultApplicationPlugin {
    private final static String MESSAGE_PATH = "messages/PdfPasswordUnlocker";

    @Override
    public String getName() {
        return AppPluginUtil.getMessage("org.joget.sample.PdfPasswordUnlocker.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return AppPluginUtil.getMessage("org.joget.sample.PdfPasswordUnlocker.pluginDesc", getClassName(), MESSAGE_PATH);
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
        return AppPluginUtil.getMessage("org.joget.sample.PdfPasswordUnlocker.pluginLabel", getClassName(), MESSAGE_PATH);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object execute(Map properties) {
        ApplicationContext ac = AppUtil.getApplicationContext();
        AppService appService = (AppService) ac.getBean("appService");
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();

        String formDefIdSourceFile = (String) properties.get("formDefIdSourceFile");
        String sourceFileFieldId = (String) properties.get("sourceFileFieldId");
        String passwordFieldId = (String) properties.get("passwordFieldId");
        String formDefIdOutputFile = (String) properties.get("formDefIdOutputFile");
        String outputFileFieldId = (String) properties.get("outputFileFieldId");
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

                // Get the source file path
                Element sourceFileEl = FormUtil.findElement(sourceFileFieldId, loadForm, formData);
                String pdfFilePath = FormUtil.getElementPropertyValue(sourceFileEl, formData);
                srcFile = FileUtil.getFile(pdfFilePath, loadForm, recordId);
                
                // Get the password from the form field
                Element passwordEl = FormUtil.findElement(passwordFieldId, loadForm, formData);
                String encryptedPassword = FormUtil.getElementPropertyValue(passwordEl, formData);
                
                // Initialize password variable
                String password = null;
                
                // Debug logging for password (mask part of it for security)
                if (encryptedPassword != null && encryptedPassword.length() > 0) {
                    String maskedPassword = encryptedPassword.length() > 2 ? 
                        encryptedPassword.substring(0, 1) + "*****" + encryptedPassword.substring(encryptedPassword.length() - 1) : "***";
                    LogUtil.info(getClassName(), "Processing PDF with password (masked): " + maskedPassword);
                    
                    // Trim the password in case there are leading/trailing spaces
                    encryptedPassword = encryptedPassword.trim();
                    // Debug log the encrypted password for troubleshooting
                    LogUtil.info(getClassName(), "Encrypted password: '" + encryptedPassword + "'");
                    
                    // Decrypt the password before using it
                    password = SecurityUtil.decrypt(encryptedPassword);
                    LogUtil.info(getClassName(), "Decrypted password being used: '" + password + "'");
                }
                
                String filePaths = srcFile.getPath();
                List<String> fileList = getFilesList(filePaths);
                StringBuilder resultBuilder = new StringBuilder();
                FormRowSet frs = new FormRowSet();

                boolean hasErrors = false;
                StringBuilder errorMessages = new StringBuilder();
                
                for (String filePath : fileList) {
                    File currentFile = new File(filePath.trim());
                    try {
                        // Use the decrypted password if available, otherwise use the encrypted one as fallback
                        byte[] unlockedPdfContent = unlockPdf(currentFile, password != null ? password : encryptedPassword);
                        String unlockedPdfFileName = writeUnlockedPdfFile(currentFile, appService, appDef, formDefIdOutputFile, recordId, unlockedPdfContent);
                        if (resultBuilder.length() > 0) {
                            resultBuilder.append(";");
                        }
                        resultBuilder.append(unlockedPdfFileName);
                    } catch (IOException ex) {
                        hasErrors = true;
                        if (errorMessages.length() > 0) {
                            errorMessages.append("; ");
                        }
                        errorMessages.append(ex.getMessage());
                        LogUtil.warn(getClassName(), "Error processing file " + currentFile.getName() + ": " + ex.getMessage());
                    }
                }

                if (!fileList.isEmpty()) {
                    FormRow row = new FormRow();
                    
                    // If there were errors, add them to the form data
                    if (hasErrors) {
                        // Store both the successful files and error messages
                        row.put(outputFileFieldId, resultBuilder.toString());
                        
                        // Log the errors for debugging
                        LogUtil.warn(getClassName(), "PDF unlock errors: " + errorMessages.toString());
                        
                        // Add error message to a form field if available
                        if (resultBuilder.length() == 0) {
                            // If no files were successfully processed, store the error message instead
                            row.put(outputFileFieldId, "ERROR: " + errorMessages.toString());
                        }
                    } else {
                        row.put(outputFileFieldId, resultBuilder.toString());
                    }
                    
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
        // Log file details for debugging
        LogUtil.info(getClassName(), "Attempting to unlock PDF file: " + pdfFile.getName() + ", size: " + pdfFile.length() + " bytes");
        // Debug log the exact password for this specific file
        LogUtil.info(getClassName(), "Using password for file '" + pdfFile.getName() + "': '" + password + "'");
        
        // Try multiple approaches to handle different PDF encryption types
        try {
            // First attempt: standard approach
            return tryUnlockWithStandardMethod(pdfFile, password);
        } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException e) {
            LogUtil.warn(getClassName(), "Standard unlock method failed for " + pdfFile.getName() + ", trying alternative methods...");
            
            try {
                // Second attempt: try with owner password
                return tryUnlockWithOwnerPassword(pdfFile, password);
            } catch (Exception e2) {
                // Third attempt: try with different loading options
                try {
                    return tryUnlockWithLoadOptions(pdfFile, password);
                } catch (Exception e3) {
                    // All attempts failed
                    LogUtil.warn(getClassName(), "All unlock methods failed for file " + pdfFile.getName());
                    throw new IOException("Unable to unlock PDF: The password provided is incorrect for file " + pdfFile.getName(), e);
                }
            }
        }
    }
    
    private byte[] tryUnlockWithStandardMethod(File pdfFile, String password) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile, password)) {
            // If the document is encrypted, save it without encryption
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                document.save(outputStream);
                return outputStream.toByteArray();
            } else {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                document.save(outputStream);
                return outputStream.toByteArray();
            }
        }
    }
    
    private byte[] tryUnlockWithOwnerPassword(File pdfFile, String password) throws IOException {
        // Some PDFs distinguish between user and owner passwords
        // Here we're trying to load with the assumption that the provided password is the owner password
        try (PDDocument document = PDDocument.load(pdfFile)) {
            if (document.isEncrypted()) {
                if (document.getCurrentAccessPermission().isOwnerPermission()) {
                    // Already have owner permissions
                    document.setAllSecurityToBeRemoved(true);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    document.save(outputStream);
                    return outputStream.toByteArray();
                } else {
                    throw new IOException("Not loaded with owner permissions");
                }
            } else {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                document.save(outputStream);
                return outputStream.toByteArray();
            }
        }
    }
    
    private byte[] tryUnlockWithLoadOptions(File pdfFile, String password) throws IOException {
        // Create custom loading options
        org.apache.pdfbox.io.MemoryUsageSetting memUsage = 
            org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly();
        
        try (PDDocument document = PDDocument.load(pdfFile, password, memUsage)) {
            if (document.isEncrypted()) {
                document.setAllSecurityToBeRemoved(true);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                document.save(outputStream);
                return outputStream.toByteArray();
            } else {
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
        
        // Log the file name for debugging
        LogUtil.info(getClass().getName(), "Generated unlocked PDF file: " + fileName);
        
        // Return just the filename, matching the colleague's approach
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
