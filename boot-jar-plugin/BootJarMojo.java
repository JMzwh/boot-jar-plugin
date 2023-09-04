package com.ksyun.train.plugins;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
/**
 *
 *@author wdw
 *@create 2023/7/13 19:21
 */
// 目标名统一为bootJar
@Mojo(name="bootJar")
public class BootJarMojo extends AbstractMojo {
    // 可自由获取maven内置变量
    @Parameter(
            defaultValue = "${settings.localRepository}",
            required = true
    )
    private String localRepository;

    @Parameter(
            property = "main.class",
            required = true
    )
    private String mainClass;

    @Component
    protected MavenProject project;

    @Override
    public void execute() throws MojoFailureException {
        getLog().info("project localRepository is" + localRepository);
        File baseDir = project.getBasedir();
        getLog().info("project base dir is" + baseDir);
        String artifactId = project.getArtifactId();
        String version = project.getVersion();
        File targetDirectory = new File(baseDir, "target");
        File classesDirectory = new File(targetDirectory, "classes");
        File libDirectory = new File(targetDirectory, "lib");
        getLog().info("project classes dir is" + classesDirectory.getAbsolutePath());
        List<File> dependencyArtifacts = project.getDependencyArtifacts()
                .stream()
                .map(Artifact::getFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        try {
            // 将第三方依赖的 Jar 包复制到 lib 目录下,并返回Jar包名
            String classPath = copyFiles(dependencyArtifacts, libDirectory);

            //拼接名称，版本
            String fileName = artifactId + "-" + version;

            // 创建可执行的 Jar 文件
            File jarFile = createJar(classesDirectory, classPath,fileName);

            // 将 Jar 文件和 lib 目录添加到 Zip 中
            createZip(targetDirectory, fileName, jarFile, libDirectory);

            //删除Jar包
            jarFile.delete();
        }  catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    //将文件内容写入
    private void writeFile(OutputStream os, File file) throws Exception {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = fis.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
        } catch (Exception e) {
            getLog().error("write file error", e);
            throw e;
        }
    }

    //创建manifest文件
    private Manifest createManifest(String mainClass, String classPath) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-version","1.0");
        manifest.getMainAttributes().putValue("Created-By","WangDaowei");
        manifest.getMainAttributes().putValue("Main-Class",mainClass);
        if(Objects.nonNull(classPath) && classPath.length() > 0){
            manifest.getMainAttributes().putValue("Class-Path", classPath);
        }
        return manifest;
    }

    //将第三方依赖包复制到lib目录
    private String copyFiles(List<File> dependencyArtifacts, File libDirectory) throws IOException {
        libDirectory.mkdirs();
        StringBuilder classPath = new StringBuilder();
        for (File dependencyArtifact : dependencyArtifacts) {
            classPath.append("lib/"+ dependencyArtifact.getName()+" ");
            File destFile = new File(libDirectory, dependencyArtifact.getName());
            try (InputStream inputStream = new FileInputStream(dependencyArtifact);
                 OutputStream outputStream = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
        }
        return classPath.toString();
    }
    //将文件添加到JarOutputStream
    private void addJarEntry(JarOutputStream jos, File file, String rootPath) throws Exception {
        try {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (Objects.isNull(files) || files.length == 0) {
                    return;
                }
                // note: separator must be /, can't be //
                if (Objects.nonNull(rootPath) && rootPath.length() > 0) {
                    rootPath = rootPath + "/";
                }
                for (File f : files) {
                    addJarEntry(jos, f, rootPath + f.getName());
                }
            } else {
                jos.putNextEntry(new JarEntry(rootPath));
                writeFile(jos, file);
            }
        } catch (Exception e) {
            getLog().error(e);
            throw e;
        }
    }

    //创建Jar包
    private File createJar(File classesDirectory,String classPath, String fileName) throws Exception {
        File jarFile = new File(classesDirectory.getParentFile(), fileName + ".jar");
        Manifest manifest = createManifest(mainClass, classPath);
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jos.putNextEntry(manifestEntry);
            manifest.write(jos);
            jos.closeEntry();
            addJarEntry(jos, classesDirectory, "");
        }
        return jarFile;
    }

    //添加到zip
    private void addZipEntry(ZipOutputStream zos, File file, String entryName) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[1024];
            int len;
            zos.putNextEntry(new ZipEntry(entryName));
            while ((len = fis.read(buf)) > 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
        } catch (IOException e) {
            throw e;
        }
    }

    //将lib目录及jar包压缩
    private void createZip(File targetDirectory, String fileName, File jarFile, File libDirectory) throws IOException {
        File zipFile = new File(targetDirectory, fileName + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addZipEntry(zos, jarFile, jarFile.getName());
            File[] libFiles = libDirectory.listFiles();
            if (libFiles != null) {
                for (File libFile : libFiles) {
                    addZipEntry(zos, libFile, "lib/" + libFile.getName());
                }
            }
        } catch (IOException e) {
            throw e;
        }
    }
}
