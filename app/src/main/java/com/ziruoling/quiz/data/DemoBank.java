package com.ziruoling.quiz.data;

import com.ziruoling.quiz.core.ParsedQuestion;
import com.ziruoling.quiz.core.QuestionType;

import java.util.ArrayList;
import java.util.List;

/** 内置示例题库（网络技术培训方向，供体验与格式参考） */
public final class DemoBank {

    private DemoBank() {}

    public static List<ParsedQuestion> build() {
        List<ParsedQuestion> list = new ArrayList<>();
        // ============ 网络基础 ============
        single(list, "网络基础", "OSI 参考模型共分为几层？",
                "4层", "5层", "6层", "7层", "C",
                "从物理层到应用层共 7 层。");
        single(list, "网络基础", "TCP 三次握手中，客户端首先发送的报文是？",
                "FIN", "SYN", "ACK", "RST", "B",
                "客户端发送 SYN 请求建立连接，服务端回 SYN+ACK，客户端再回 ACK。");
        single(list, "网络基础", "IPv4 地址由多少位二进制数组成？",
                "16 位", "32 位", "64 位", "128 位", "B",
                "IPv4 为 32 位，IPv6 为 128 位。");
        single(list, "网络基础", "ARP 协议的主要作用是？",
                "域名解析", "将 IP 地址解析为 MAC 地址", "端口映射", "地址自动分配", "B",
                "ARP（地址解析协议）根据 IP 地址解析对应的 MAC 地址。");
        multi(list, "网络基础", "下列属于传输层协议的有？",
                "IP", "TCP", "UDP", "ICMP", "BC",
                "IP 与 ICMP 属于网络层。");
        judge(list, "网络基础", "UDP 是面向连接的传输层协议。", false,
                "UDP 是无连接协议，TCP 才是面向连接的。");

        // ============ 光纤接入 ============
        single(list, "光纤接入", "FTTH 的中文含义是？",
                "光纤到楼", "光纤到户", "光纤到交接箱", "光纤到路边", "B",
                "FTTH = Fiber To The Home，光纤到户。");
        single(list, "光纤接入", "家用光猫（ONT）上线通常依靠哪种认证？",
                "PON 注册认证", "PPPoE 认证", "DHCP 认证", "WPA2 认证", "A",
                "ONT 通过 PON 系统注册认证（如 LOID/SN 注册）后分配业务。");
        single(list, "光纤接入", "测量光信号功率常用的单位是？",
                "dBm", "MHz", "V", "A", "A",
                "光功率常用 dBm 表示。");
        multi(list, "光纤接入", "下列属于 PON 接入网设备的有？",
                "OLT", "ONT", "光分路器", "DSLAM", "ABC",
                "OLT（局端）、ONT（用户端）、光分路器（无源分光）构成 PON 网络。");
        judge(list, "光纤接入", "光纤弯曲半径过小会导致光功率损耗增大。", true,
                "光纤弯曲过小会产生弯曲损耗，光功率明显下降。");
        fill(list, "光纤接入", "光纤到户的英文缩写是____。", "FTTH", "");

        // ============ 网络与交换 ============
        single(list, "网络与交换", "路由器的主要功能是？",
                "提供无线信号", "在不同网络间路由转发数据", "将光信号转换为电信号", "加密所有流量", "B",
                "路由器工作在网络层，负责不同网络之间的路由与转发。");
        single(list, "网络与交换", "交换机工作在 OSI 参考模型的哪一层？",
                "物理层", "数据链路层", "网络层", "传输层", "B",
                "交换机主要工作在数据链路层（二层），依据 MAC 地址转发。");
        single(list, "网络与交换", "VLAN 技术的主要作用是？",
                "提高无线速率", "隔离广播域", "加密数据传输", "扩展传输距离", "B",
                "VLAN 划分后各虚拟局域网之间默认不能二层互通，广播被隔离。");
        single(list, "网络与交换", "无线终端接入路由器时，路由器通过什么地址识别设备？",
                "IP 地址", "MAC 地址", "端口号", "域名", "B",
                "无线客户端以 MAC 地址作为身份标识接入。");
        multi(list, "网络与交换", "家用无线路由器通常具备哪些功能？",
                "NAT 地址转换", "DHCP 服务", "无线接入", "光功率转换", "ABC",
                "光功率转换属于光猫（ONT）的功能。");
        multi(list, "网络与交换", "下列哪些方法可以扩展家庭 WiFi 覆盖？",
                "无线中继", "电力猫扩展", "有线级联 AP", "增加宽带带宽", "ABC",
                "增大带宽不能扩展覆盖范围。");
        judge(list, "网络与交换", "开启路由器访客网络后，访客与主网络默认相互隔离。", true,
                "访客网络默认隔离，保护主网设备安全。");

        // ============ 专线产品 ============
        single(list, "专线产品", "企业专线相比普通家宽的优势不包括？",
                "固定公网 IP", "上下行带宽对等", "带宽可伸缩", "资费更低", "D",
                "专线资费通常高于普通宽带，但其保障等级与稳定性更高。");
        single(list, "专线产品", "企业专线常用带宽单位为？",
                "Mbps", "KB", "GB", "Hz", "A",
                "专线带宽以 Mbps 为单位计量。");
        single(list, "专线产品", "MPLS 专线的转发方式是？",
                "按 IP 最长匹配转发", "按标签（Label）转发", "按 MAC 转发", "按端口转发", "B",
                "MPLS 在网络层与链路层之间插入标签，按标签交换转发。");
        multi(list, "专线产品", "下列属于中国电信政企专线业务的有？",
                "互联网专线", "MPLS-VPN 专线", "云专线", "家庭宽带", "ABC",
                "家庭宽带属于个人客户产品。");
        judge(list, "专线产品", "政企互联网专线可申请固定的公网 IP 地址。", true,
                "专线支持静态公网 IP，便于对外发布服务。");
        judge(list, "专线产品", "专线出现故障时，只需运营商单方测试即可定位问题。", false,
                "通常需要用户端配合（设备状态、网口灯、测速等）才能快速定位。");
        fill(list, "专线产品", "企业申请专线业务时，通常需提供____等资质材料。", "营业执照", "");

        // ============ 网络检测 ============
        single(list, "网络检测", "检测主机是否连通网络，最常用的命令是？",
                "ping", "tracert", "netstat", "nslookup", "A",
                "ping 用于测试网络连通性与往返时延。");
        single(list, "网络检测", "Windows 下查看路由路径的命令是？",
                "ipconfig", "tracert", "route print", "netstat", "B",
                "tracert（Linux 为 traceroute）逐跳显示到达目标的路径。");
        multi(list, "网络检测", "下列属于网络诊断常用工具的有？",
                "ping", "tracert", "网速测试工具", "光功率计", "ABCD",
                "从连通性、路由、带宽、光链路四个层面排查。");
        judge(list, "网络检测", "tracert 命令可以显示数据包经过的每一跳路由器。", true, "tracert 显示每跳地址与延迟。");
        fill(list, "网络检测", "丢包率的常用单位是____。", "百分比||%", "");
        fill(list, "网络检测", "排查光缆故障时，通常使用____表测量光功率。", "光功率计", "");
        shortQ(list, "网络检测", "简述客户报障前的自查流程要点。",
                "先检查光猫/路由器电源与指示灯是否正常；重启光猫和路由器；用 ping 测网关与外网连通性，"
                        + "使用测速工具确认带宽；确认为线路/设备故障后再联系客服，并提供设备型号、故障现象、"
                        + "指示灯状态与检测结果。");
        shortQ(list, "网络检测", "客户反馈网速慢，请列出排查思路。",
                "①区分是单台设备还是全屋慢；②对比有线/无线速率差异；③用测速工具核实带宽是否符合套餐；"
                        + "④检查光猫/路由器负荷、散热与后台占用；⑤检查光功率与线路；⑥必要时更换光猫或路由器测试。");

        // ============ 排序题 ============
        sort(list, "网络基础", "将 TCP 三次握手的步骤按发生顺序排列（答案填字母顺序，如 CBA）。",
                new String[]{"客户端回复 ACK 确认", "服务端回复 SYN+ACK", "客户端发送 SYN 请求"},
                "CBA", "建立连接：SYN → SYN+ACK → ACK。");
        sort(list, "网络检测", "客户报障“上不了网”，请将排查步骤按推荐顺序排列。",
                new String[]{"查看光猫指示灯状态", "重启光猫与路由器", "ping 测试网关与外网连通性", "联系客服并反馈检测结果"},
                "BADC", "先看灯、再重启、后测试，最后才报障，效率最高。");

        // ============ 看图题 ============
        look(list, "网络与交换", "（看图题）请描述常见家庭组网中，光猫、路由器与终端之间的连接关系与信号转换。",
                "光纤入户接入光猫（ONT），完成光信号→电信号转换；光猫 LAN 口（或桥接模式下经路由器 PPPoE 拨号）"
                        + "连接无线路由器，由路由器完成 NAT、DHCP 与无线接入，手机/电脑等终端通过 WiFi 或网线接入路由器。"
                        + "提示：图片可通过 Word 表格题库贴图导入。");

        return list;
    }

    private static void single(List<ParsedQuestion> l, String cat, String content,
                               String a, String b, String c, String d, String ans, String analysis) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.SINGLE;
        q.content = content;
        q.category = cat;
        q.options.add(a);
        q.options.add(b);
        q.options.add(c);
        q.options.add(d);
        q.answer = ans;
        q.analysis = analysis;
        l.add(q);
    }

    private static void multi(List<ParsedQuestion> l, String cat, String content,
                              String a, String b, String c, String d, String ans, String analysis) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.MULTI;
        q.content = content;
        q.category = cat;
        q.options.add(a);
        q.options.add(b);
        q.options.add(c);
        q.options.add(d);
        q.answer = ans;
        q.analysis = analysis;
        l.add(q);
    }

    private static void judge(List<ParsedQuestion> l, String cat, String content, boolean correct, String analysis) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.JUDGE;
        q.content = content;
        q.category = cat;
        q.answer = correct ? "A" : "B";
        q.analysis = analysis;
        l.add(q);
    }

    private static void fill(List<ParsedQuestion> l, String cat, String content, String answer, String analysis) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.FILL;
        q.content = content;
        q.category = cat;
        q.answer = answer;
        q.analysis = analysis;
        l.add(q);
    }

    private static void shortQ(List<ParsedQuestion> l, String cat, String content, String answer) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.SHORT;
        q.content = content;
        q.category = cat;
        q.answer = answer;
        l.add(q);
    }

    private static void sort(List<ParsedQuestion> l, String cat, String content,
                             String[] opts, String answer, String analysis) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.SORT;
        q.content = content;
        q.category = cat;
        for (String o : opts) q.options.add(o);
        q.answer = answer;
        q.analysis = analysis;
        l.add(q);
    }

    private static void look(List<ParsedQuestion> l, String cat, String content, String answer) {
        ParsedQuestion q = new ParsedQuestion();
        q.type = QuestionType.OTHER;
        q.content = content;
        q.category = cat;
        q.answer = answer;
        l.add(q);
    }
}