
# 已经完成：
- 使用FPGrowth来得到频繁项集。算法来自https://github.com/felipekunzler/frequent-itemset-mining-spark
- 求出了频繁项集的支持度
- 利用支持度生成关联规则
- 从用户特征中给出推荐

# 未完成：
- 配置Hadoop

# 项目使用方式
- 安装java 8。更高版本的java与spark不兼容
- 安装并配置sbt。记得将java的内存设置的大一点，否则会溢出。可以参考https://blog.csdn.net/a532672728/article/details/72477591
- 在项目目录下，运行sbt run。此命令会自动根据build.sbt中的依赖下载相应的环境，但由于我已经把下载的依赖包一并上传了，因此会直接开始编译并执行
- 选择Main Object执行。
- 由于数据集比较大，因此没有上传完整的数据集，项目里只有测试集，放在dataset目录下。

# 其他
- 由于Spark自带的FPGrowth十分慢（我试了以下，40min都没有完成。相应代码在Main.scala.bak_new中，而项目中的算法可以在几分钟内完成），因此不采用自带的算法
