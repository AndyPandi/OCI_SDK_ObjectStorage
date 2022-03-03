package com.odp.controller;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ListObjects;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import com.oracle.bmc.objectstorage.transfer.DownloadConfiguration;
import com.oracle.bmc.objectstorage.transfer.DownloadManager;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadResponse;

@Controller
public class ObjStorageController {
	
	// 인덱스 화면 
	@GetMapping(value = "/index.do")
	public String index() {
		return "index";
	}
	
	
	// 오브젝트 리스트 조회 화면 
	@PostMapping(value = "/objList.do")
	public String getObjectList(Model model, @RequestParam String namespaceName, @RequestParam String bucketName) throws Exception {
		System.out.println("namespaceName : " + namespaceName);
		System.out.println("bucketName : " + bucketName);
		
		// OCI API Key 설정 후 해당 Profile 이 있는 Config 파일경로, Profile 명 입력 (Config 파일내 private key 경로도 체크)
		final ConfigFile config = ConfigFileReader.parse("~/workspace_sts/OCI_SDK_TEST/src/main/resources/static/config", "DEFAULT");
		final ConfigFileAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(config);		
		ObjectStorage client = new ObjectStorageClient(provider);
		client.setRegion(Region.AP_SEOUL_1);
		
        ListObjectsRequest request =
        		ListObjectsRequest.builder()
        			.namespaceName(namespaceName)
        			.bucketName(bucketName)
        			.fields("size, md5, timeCreated, timeModified")
        			.build();
        
        ListObjectsResponse response = client.listObjects(request);
        
        ListObjects list = response.getListObjects();
        List<ObjectSummary> objectList = list.getObjects();
        
        
        for(int i=0; i<objectList.size(); i++) {
        	System.out.println("--------------------------------------------------");
        	System.out.println("getName : " + objectList.get(i).getName());
        	System.out.println("getSize : " + objectList.get(i).getSize());
        	System.out.println("getTimeCreated : " + objectList.get(i).getTimeCreated());
        	System.out.println("getTimeModified : " + objectList.get(i).getTimeModified());
        }
        System.out.println("--------------------------------------------------");
        
        client.close();
		
        model.addAttribute("namespaceName", namespaceName);
        model.addAttribute("bucketName", bucketName);
        model.addAttribute("objectList", objectList);
        
		return "fileList";
	}
	
	// 오브젝트 다운로드 화면 
	@PostMapping(value = "/objDownload.do")
	public ResponseEntity<Resource> downloadObject(@RequestHeader("User-Agent") String agent
												, @RequestParam String namespaceName
												, @RequestParam String bucketName
												, @RequestParam String objectName) throws Exception {
		
		System.out.println("namespaceName : " + namespaceName);
		System.out.println("bucketName : " + bucketName);
		System.out.println("objectName : " + objectName);
		
        String outputFileName = objectName;
        
		// OCI API Key 설정 후 해당 Profile 이 있는 Config 파일경로, Profile 명 입력 (Config 파일내 private key 경로도 체크)
		final ConfigFile config = ConfigFileReader.parse("~/workspace_sts/OCI_SDK_TEST/src/main/resources/static/config", "DEFAULT");
		final ConfigFileAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(config);		
		ObjectStorage client = new ObjectStorageClient(provider);
		client.setRegion(Region.AP_SEOUL_1);
		
        DownloadConfiguration downloadConfiguration =
        		DownloadConfiguration.builder()
        			.parallelDownloads(3)
        			.maxRetries(3)
        			.multipartDownloadThresholdInBytes(6 * 1024 *1024)
        			.partSizeInBytes(4 *1024 * 1024)
        			.build();
        		
        DownloadManager downloadManager = new DownloadManager(client, downloadConfiguration);
        
        GetObjectRequest request =
        		GetObjectRequest.builder()
        			.namespaceName(namespaceName)
        			.bucketName(bucketName)
        			.objectName(objectName)
        			.build();
        
        GetObjectResponse response = downloadManager.downloadObjectToFile(request, new File(outputFileName));
        client.close();
        
        System.out.println("--------------------------------------------------");      
        System.out.println("getContentType : "+response.getContentType());
        System.out.println("--------------------------------------------------");
        
        //if(!file.exists()) return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        //브라우저별 한글파일 명 처리
        String onlyFileName = outputFileName;
        
        // Internet Explore
        if(agent.contains("Trident"))
            onlyFileName = URLEncoder.encode(onlyFileName, "UTF-8").replaceAll("\\+", " ");
        // Micro Edge
        else if(agent.contains("Edge"))
            onlyFileName = URLEncoder.encode(onlyFileName, "UTF-8");
        // Chrome
        else
            onlyFileName = new String(onlyFileName.getBytes("UTF-8"), "ISO-8859-1");
        //브라우저별 한글파일 명 처리
        
        Resource resource = new FileSystemResource(new File(outputFileName));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, response.getContentType());
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFileName);
        
        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
	}
	
	
	// 오브젝트 업로드 처리 
	@PostMapping(value = "/objUpload.do")
	public String uploadObject(@RequestParam String namespaceName, @RequestParam String bucketName, @RequestParam MultipartFile uploadFile) throws IOException {
		System.out.println("namespaceName : " + namespaceName);
		System.out.println("bucketName : " + bucketName);
		
		String objectName = null;
        String contentType = null;
		File file = null;
		
		if(!uploadFile.isEmpty()) {
			System.out.println("getOriginalFilename : " + uploadFile.getOriginalFilename());
			System.out.println("getContentType : " + uploadFile.getContentType());
			
			objectName = uploadFile.getOriginalFilename();
			contentType = uploadFile.getContentType();
			
			file = convert(uploadFile);
		}		
        
		// OCI API Key 설정 후 해당 Profile 이 있는 Config 파일경로, Profile 명 입력 (Config 파일내 private key 경로도 체크)
		final ConfigFile config = ConfigFileReader.parse("~/workspace_sts/OCI_SDK_TEST/src/main/resources/static/config", "DEFAULT");
		final ConfigFileAuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(config);		
		ObjectStorage client = new ObjectStorageClient(provider);
		client.setRegion(Region.AP_SEOUL_1);
		
		UploadConfiguration uploadConfiguration = UploadConfiguration.builder()
												.allowMultipartUploads(true)
												.allowParallelUploads(true)
												.build();
		
		UploadManager uploadManager = new UploadManager(client, uploadConfiguration);
		
		PutObjectRequest request = PutObjectRequest.builder()
									.bucketName(bucketName)
									.namespaceName(namespaceName)
									.objectName(objectName)
									.contentType(contentType)
									.contentLanguage(null)
									.contentEncoding(null)
									.opcMeta(null)
									.build();
				
		UploadRequest uploadRequest = UploadRequest.builder(file).allowOverwrite(true).build(request);
		
		UploadResponse response = uploadManager.upload(uploadRequest);
		System.out.println("response : "+response);
		
		return "index";
	}
	
	
	// MultipartFile(Spring) 에서 File(Java.io) 로 변환 
	public File convert(MultipartFile file) throws IOException
	{    
	    File convFile = new File(file.getOriginalFilename());
	    convFile.createNewFile(); 
	    FileOutputStream fos = new FileOutputStream(convFile); 
	    fos.write(file.getBytes());
	    fos.close(); 
	    return convFile;
	}
}
