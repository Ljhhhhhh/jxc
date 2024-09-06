# 插件

`@/plugin` 中放的是即使删掉也不会影响运行的东东，都通过`import().then()`的方式来引用。

## canvas动画

所有动画位于 `@/plugin/canvasAnimation`下，每一个文件夹都会导出一个`Animation`对象，
使用 `new Animation(canvas)`的方式来创建一个动画，想停止的话调用 `Animation.stop()` 即可。

## 图片压缩

支持 `png`、`jpg` 两种格式的图片，使用方法参考 `@/view/example/test/develop/component/ImageCompressTest.vue`。

::: tip
由于是以 `worker` 的形式调用，所以需要准备 `cjpeg.min.js` 和 `pngquant.min.js`，这两个文件都放在项目的 `public`下。
:::

## 其他

此外项目中还有 `live2d` 和 `webgl` 两个插件，有兴趣的可以自行查看。
