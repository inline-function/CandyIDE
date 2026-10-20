# 容器

凝华内置一套纯函数式容器库，就在标准库 `core/collection/`（List/Set/Map/Optional/Result/Tuple/类型类同模块互见）：全程不可变、方法链、挂载后直接可用。

> v4 终版：lambda 与 when 分支一律用 `=>`（旧 `->` 已废除；lambda 写 `->` 仍可解析，但文档统一 `=>`）。

## 清单

| 容器 | 构造子 | 说明 |
|------|--------|------|
| `List[T]` | `Nil` / `Cons(h, t)` | 链表（脚本/编译模式均表示为数组值） |
| `Set[T]` | `SetNil` / `SetCons(x, t)` | 去重集合（结构枚举，contains 判重） |
| `Map[K, V]` | `MapNil` / `MapCons(k, v, t)` | 关联表，`put` 覆盖同键旧值 |
| `Optional[T]` | `Some(v)` / `None` | 二值枚举（见《空安全》） |
| `Result[T, E]` | `Ok(v)` / `Err(e)` | 成功/失败（错误处理） |
| 元组 `(A, B)` | `(a, b)` 字面量 | 固定多值 |

## 构造

```subl
var xs = listOf(1, 2, 3)                       // List（变长实参）
var ys = range(1, 5)                           // 区间列表 [1,2,3,4]（右开）
var s  = toSet(listOf(1, 2, 3))                // Set
var m  = toMap(listOf(("a", 1), ("b", 2)))     // 从元组列表建 Map
@mut var m2: Map[Str, Nat] = mapEmpty()        // 空 Map（标注类型后类型参数确定）
```

> 容器不可变，`m2 = m2.put(...)` 这种整体替换要把变量声明为 `@mut`。
> 调用时**不能写显式类型实参**（`mapEmpty[Str, Nat]()` 不是合法语法）——类型参数由形参/标注推导。

## 列表常用方法

```subl
xs.map({ x => x + 1 })                      // [2, 3, 4]
xs.filter({ x => x > 1 })                   // [2, 3]
xs.fold(0) { acc, x => acc + x }            // 6（尾随 lambda）
xs.flatMap({ x => listOf(x, x) })           // 展平映射
xs.size() / xs.isEmpty()                    // 长度 / 判空
xs.contains(3) / xs.indexOf(3)              // 查找
xs.head() / xs.last() / xs.elementAt(1)     // 取元素（返回 Optional）
xs.firstOr(0)                               // 首元素兜底
xs.count { x => x > 1 }                       // 计数
xs.any { x => x > 2 } / xs.all { x => x > 0 }  // 存在 / 全部
xs.find { x => x > 1 }                      // 首个满足者（Optional）
xs.forEach { x => print(x) }                // 遍历（@impure lambda）
xs.take(2) / xs.drop(1) / xs.slice(1, 3)    // 取/丢/切片
xs.reverse() / xs.distinct() / xs.sortBy({ a, b => a < b })
xs.append(ys) / xs.zip(ys)                  // 拼接 / 拉链
xs.joinToString("-")                        // "1-2-3"
xs.sum()                                    // 6（List[Nat]）
xs.maxNat() / xs.minNat()                   // Optional
```

## Set / Map 常用方法

```subl
s.add(4) / s.remove(1)                       // 增删（返回新 Set）
s.contains(2)                                // true
s.union(other) / s.intersect(other)          // 并 / 交
s.flatMap { x => toSet(listOf(x, x)) }       // 展平映射
s.toList() / toSet(xs)                       // 与 List 互转
s.size() / s.isEmpty() / s.count { ... }

m.get("a")                                   // Some(1)
m.containsKey("a")                           // 判键
m.put("a", 2) / m.remove("a")                // 写/删（返回新 Map；put 覆盖同键旧值）
m.keys() / m.values()                        // List
m.size() / m.isEmpty()                       // 键数
m.filter({ v => v > 1 })                     // 按值过滤
m.mapValues({ v => v + 1 }) / m.mapKeys({ k => k + "!" })
m.merge(other)                               // 合并（other 的同键覆盖）
```

> Set 没有索引语义，`toStr` 打印出的是内部 `SetCons` 链结构——要看好不好看就 `toList()` 再 `joinToString`。

## Result（错误处理）

```subl
var r: Result[Nat, Str] = Ok(42)
r.isOk()                                     // true
r.isErr()                                    // false
r.unwrapOr(0)                                // 42（Err 时取默认）
r.mapOk({ v => v + 1 })                      // Ok(43)——只变换成功值
r.mapErr({ e => "E:" + e })                  // 只变换错误值
r.flatMapOk({ v => Ok(v * 2) })              // 成功路径链式（Err 短路）
```

Result 也能 `when` 解构（`is Ok(v)` / `is Err(e)`）；二阶类型（T×E 双参数）套不了 `F[_]` 一阶类型类，所以映射走上面的 mapOk/flatMapOk：

```subl
when (r) {
    is Ok(v)  => print(v)
    is Err(e) => print("错: " + e)
}
```

## 类型类：Functor / Foldable / Monad

List/Set/Map/Optional 共享一套类型类接口（`core/collection/typeclass.subl`）：

| 类型类 | 方法 | 语义 |
|--------|------|------|
| `Functor[F[_]]` | `map(fa, f)` | 保持容器形状、只变换元素 |
| `Foldable[F[_]]` | `fold(fa, init, f)` | 左折叠累积 |
| `Monad[F[_]]` | `pure(x)` / `flatMap(fa, f)` | 单子：注入与展平 |

`impl Functor for List[a]` 等提供实例（类型类方法的第一形参即接收者，方法糖 `xs.map(f)` 与自由式 `map(xs, f)` 路由到同一实现）。

> 注意：`size()`/`isEmpty()`/`contains()` 等通用工具是**按容器重载的同名自由函数**（接收者路由），不是某个 `Collection` 类型类的成员。

## 元组

```subl
var p = ("甲", 1)                    // (Str, Nat)
when (p) { (a, b) => print(a + toStr(b)); else => print("?") }
zip(listOf("甲", "乙"), listOf(1, 2))   // [("甲",1), ("乙",2)]
```

## 一句话

容器全程**不可变**：`put`/`add`/`remove` 都返回新容器，原容器不变。推荐链式写法 `xs.map(...).filter(...)`。

> 试手：把用到容器的片段写成 `.subs` 脚本，放进一个含 `core/` 副本的目录，`run-script <目录>` 直接跑（core 隐式挂载）。单文件 `check` 不挂标准库——那种场合要在文件内自声明 `enum List[T] { ... }`。
