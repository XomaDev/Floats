#include <iostream>
#include <string>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <jni.h>
#include <fstream>
#include <fcntl.h>
#include <random>
#include <sstream>


constexpr int BUFFER_SIZE = 1000000;

bool wasCancelled = false;


int
readBytes(
        bool retried,
        JNIEnv *pEnv,
        jobject callback,
        int sock,
        int file,
        ssize_t read,
        jmethodID methodId
);

extern "C"
JNIEXPORT void JNICALL
Java_com_baxolino_apps_floats_core_files_FileReceiveService_cancelFileReceive(JNIEnv *env,
                                                                              jobject thiz) {
   wasCancelled = true;
}

jstring receiveContentSocket(
        jobject callback,
        JNIEnv *env,
        jstring outputDir,
        jstring fileName,
        jint expectedSize,
        jstring host,
        jint port,
        bool retry
) {

   const char *hostStr = env->GetStringUTFChars(host, nullptr);
   const char *outputPath = env->GetStringUTFChars(outputDir, nullptr);
   const char *fileNameChars = env->GetStringUTFChars(fileName, nullptr);

   std::string hostName(hostStr);
   std::string outputPathStr(outputPath);

   int portNumber = static_cast<int>(port);
   int sizeExpected = static_cast<int>(expectedSize);

   // Create a socket
   int sock = socket(AF_INET, SOCK_STREAM, 0);
   if (sock == -1)
      return env->NewStringUTF("Failed to create socket.");


   // Set up the server address
   sockaddr_in serverAddress{};
   serverAddress.sin_family = AF_INET;
   serverAddress.sin_port = htons(portNumber);

   if (inet_pton(AF_INET, hostName.c_str(), &(serverAddress.sin_addr)) <= 0)
      return env->NewStringUTF("Failed to set up server address.");


   // Connect to the server
   if (connect(sock, (struct sockaddr *) &serverAddress, sizeof(serverAddress)) < 0) {
      if (retry) {
         // we will retry connection again, the server may not be ready yet
         // .5 second -> 500000 micro secs
         usleep(500000);

         return receiveContentSocket(
                 callback,
                 env,
                 outputDir,
                 fileName,
                 expectedSize,
                 host,
                 port,
                 false);
      }
      return env->NewStringUTF("Failed to connect to the server.");
   }


   std::string finalOutput = outputPathStr + "/" + fileNameChars;

   int outputFile = open(finalOutput.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
   if (outputFile == -1) {
      // Failed to open the output file
      if (errno == 17) {
         // retry opening it with appended random name
         std::random_device rd;
         std::mt19937 generator(rd());
         std::uniform_int_distribution<int> distribution(1000000, 9999999);
         std::ostringstream oss;
         oss << distribution(generator);
         std::string outputPathWithRandomNumber = outputPathStr + "/(" + oss.str() + ") " +  fileNameChars;

         finalOutput = outputPathWithRandomNumber;
         outputFile = open(outputPathWithRandomNumber.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0644);
         if (outputFile == -1) {
            const char *errorMessage = strerror(errno);
            return env->NewStringUTF(
                    (std::string(errorMessage)
                     + " "
                     + std::to_string(errno)
                     + " "
                     + outputPathWithRandomNumber).c_str());
         }
      }
   }

   jclass clazz = env->GetObjectClass(callback);
   env->CallVoidMethod(callback, env->GetMethodID(clazz, "onStart", "()V"));

   ssize_t nRead = 0;

   jmethodID methodId = env->GetMethodID(clazz, "update", "(I)V");

   int result = readBytes(
           false,
           env,
           callback,
           sock,
           outputFile,
           nRead,
           methodId
   );
   if (result == -1) {
      return env->NewStringUTF(("Failed to write to file: " + std::to_string(result)).c_str());
   }

   if (wasCancelled) {
      jmethodID cancelId = env->GetMethodID(clazz, "onCancelledNative", "()V");
      env->CallVoidMethod(callback, cancelId);


      return env->NewStringUTF("Was Cancelled");
   }

   // Close the output file
   close(outputFile);

   // Close the socket
   close(sock);

   env->ReleaseStringUTFChars(host, hostStr);
   env->ReleaseStringUTFChars(outputDir, outputPath);
   env->ReleaseStringUTFChars(fileName, fileNameChars);

   if (sizeExpected != result) {
      return env->NewStringUTF(("Transfer was disrupted. " + std::to_string(result)).c_str());
   }

   // WARNING:
   //  editing this can cause various problems
   //  in the file receiver service
   return env->NewStringUTF(("successful " + finalOutput).c_str());
}


int readBytes(
        bool retried,
        JNIEnv *env,
        jobject callback,
        int sock,
        int outputFile,
        ssize_t nRead,
        jmethodID methodId
) {
   char buffer[BUFFER_SIZE];

   ssize_t bytesRead;
   while ((bytesRead = read(sock, buffer, BUFFER_SIZE)) > 0) {
      ssize_t bytesWritten = write(outputFile, buffer, bytesRead);
      if (bytesWritten != bytesRead) {
         close(outputFile);
         close(sock);
         return -1;
      }
      nRead += bytesRead;
      env->CallVoidMethod(callback, methodId, nRead);
   }
   if (nRead == 0) {
      // sometimes what happens is, the sender device is sometimes
      // slow to start uploading file contents; so we retry receiving
      // it, one more time
      if (!retried) {
         // .5 second -> 500000 micro secs
         usleep(4000000);
         return readBytes(
                 true,
                 env,
                 callback,
                 sock,
                 outputFile,
                 nRead,
                 methodId
         );
      }
      return -2;
   }
   return nRead;
}


extern "C"
JNIEXPORT jstring JNICALL
Java_com_baxolino_apps_floats_core_files_FileReceiveService_receiveFile(JNIEnv *env,
                                                                        jobject thiz,
                                                                        jstring output_dir,
                                                                        jstring file_name,
                                                                        jint expected_size,
                                                                        jstring host, jint port) {
   wasCancelled = false;

   return receiveContentSocket(
           thiz,
           env,
           output_dir,
           file_name,
           expected_size,
           host,
           port,
           true);
}
