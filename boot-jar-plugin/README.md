# boot-jar-plugin

项目功能:编写一个插件构建项目zip包，以下为项目描述：

- 插件生成的zip包路径位于项目根目录下的target文件夹
- 插件必须基于maven生命中周期的compile阶段生成的target/classes才能完成工作
- zip文件中包含可执行的jar包，以及lib目录（里面包含运行依赖的第三方Jar包）
