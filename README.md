# PDF Password Unlocker Plugin for Joget DX8

A Joget plugin that unlocks password-protected PDF files within your workflow processes.

## Description

The PDF Password Unlocker is a Joget application plugin that allows users to remove password protection from PDF files. This plugin integrates with Joget's workflow system to seamlessly handle password-protected PDFs within your business processes.

## Installation

1. Download the plugin JAR file
2. Log in to Joget as an administrator
3. Go to Manage Apps > Manage Plugins
4. Upload and install the plugin JAR file
5. Restart the Joget server

## Usage

### Configuration

When designing your app in Joget App Designer:

1. Add a form with:
   - A file upload field for the PDF file
   - A password field for the PDF password

2. Add a process tool and select "PDF Password Unlocker"

3. Configure the plugin:
   - **Source**: Select the form and file upload field containing the protected PDF
   - **Password**: Select the form field where users will enter the password
   - **Output**: Select the destination form and field for the unlocked PDF

### Runtime Usage

1. Users upload a password-protected PDF through the form
2. Users enter the PDF password in the password field
3. When the process runs, the plugin unlocks the PDF
4. The unlocked PDF is saved to the specified output location

## Requirements

- Joget DX8
- Java 8 or above

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Acknowledgments

- Developed as part of an internship at Joget Inc.
- A reverse engineering of the [Password Protected PDF Tool plugin](https://github.com/jogetoss/password-protected-pdf-tool)