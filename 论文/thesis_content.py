# -*- coding: utf-8 -*-
"""毕业论文正文内容（片刻帮 / SpringCloud+Vue 校园便民跑腿平台）。"""

META = {
    "school": "广州工商学院",
    "college": "AI工程师学院",
    "major": "软件工程",
    "student": "黄俊达",
    "student_id": "202316510118",
    "advisor": "杜鑫磊",
    "advisor_title": "硕士",
    "title": "基于SpringCloud+Vue的校园便民跑腿平台的设计与实现",
    "title_en": "Design and Implementation of a Campus Convenience Errand Platform Based on SpringCloud and Vue",
    "year": "2026",
}

CN_ABSTRACT = (
    "随着高校校园规模扩大与学生学习生活节奏加快，快递代取、代买餐食、代打印、代排队等互助型跑腿需求日益旺盛。"
    "传统依赖微信群接龙、口头约定的方式存在信息分散、接单冲突、资金缺乏保障、履约难追溯等问题，难以支撑规范化运营。"
    "针对上述问题，本文设计并实现了一套基于SpringBoot与SpringCloud的校园便民跑腿平台（片刻帮）。"
    "系统采用前后端分离架构：后端基于SpringCloud Alibaba将业务拆分为网关、认证、用户、任务、审核、聊天、钱包等微服务，"
    "并集成Nacos服务注册与配置、SpringCloud Gateway统一路由与JWT鉴权、OpenFeign服务调用、Sentinel限流熔断、"
    "Seata分布式事务及RabbitMQ异步通知；前端基于Vue 3分别构建用户主站、接单员工作台与客服工作台，"
    "通过WebSocket实现实时沟通，并结合支付宝沙箱完成充值与赏金支付，借助Ollama支持任务智能审核与智能客服。"
    "系统面向管理员、接单员、普通用户与人工客服四类角色，覆盖任务全生命周期管理、钱包与优惠券、信誉分管理、"
    "申诉处理、公告统计及多角色运营管理等功能。测试结果表明，系统能够稳定完成任务发布与接单、跨服务扣款、"
    "实时消息推送等关键流程，可为校园互助服务的信息化建设提供可落地的工程参考。"
)

CN_KEYWORDS = "校园跑腿；微服务；SpringCloud；Vue；WebSocket"

EN_ABSTRACT = (
    "With the expansion of university campuses and the acceleration of students’ daily routines, "
    "mutual-aid errand demands such as parcel pickup, meal purchasing, printing and queueing have grown rapidly. "
    "Traditional WeChat-group coordination suffers from fragmented information, conflicting orders, weak payment guarantees "
    "and poor traceability. To address these issues, this thesis designs and implements a campus convenience errand platform "
    "(Pianke Bang) based on SpringBoot and SpringCloud. The system follows a front-end/back-end separated architecture. "
    "The back end adopts SpringCloud Alibaba microservices including gateway, authentication, user, task, audit, chat and wallet "
    "services, integrated with Nacos, SpringCloud Gateway with JWT, OpenFeign, Sentinel, Seata and RabbitMQ. "
    "The front end uses Vue 3 to build a user portal, a rider console and a customer-service console, supports real-time messaging "
    "via WebSocket, Alipay sandbox payments, and Ollama-assisted auditing and smart support. "
    "Tests show that core workflows such as task publishing and claiming, cross-service payment and real-time messaging are stable, "
    "providing a practical engineering reference for campus mutual-aid informatization."
)

EN_KEYWORDS = "campus errand; microservice; SpringCloud; Vue; WebSocket"

TOC_ENTRIES = [
    ("摘  要", 1),
    ("Abstract", 1),
    ("1 绪论", 1),
    ("1.1 研究背景与意义", 2),
    ("1.2 国内外研究现状", 2),
    ("1.3 研究内容与方法", 2),
    ("1.4 论文组织结构", 2),
    ("2 相关技术介绍", 1),
    ("2.1 SpringBoot", 2),
    ("2.2 SpringCloud Alibaba", 2),
    ("2.3 Vue.js", 2),
    ("2.4 MySQL", 2),
    ("2.5 Nacos与SpringCloud Gateway", 2),
    ("2.6 OpenFeign、Sentinel与Seata", 2),
    ("2.7 RabbitMQ与WebSocket", 2),
    ("2.8 支付宝开放平台与Ollama", 2),
    ("3 系统分析", 1),
    ("3.1 可行性分析", 2),
    ("3.1.1 经济可行性", 3),
    ("3.1.2 技术可行性", 3),
    ("3.1.3 操作可行性", 3),
    ("3.2 功能需求分析", 2),
    ("3.2.1 认证与用户中心模块", 3),
    ("3.2.2 任务发布与接单模块", 3),
    ("3.2.3 钱包支付与优惠券模块", 3),
    ("3.2.4 实时聊天与消息模块", 3),
    ("3.2.5 智能客服与人工客服模块", 3),
    ("3.2.6 管理员模块", 3),
    ("3.3 非功能需求分析", 2),
    ("3.3.1 性能需求", 3),
    ("3.3.2 安全性需求", 3),
    ("3.3.3 可维护性与可扩展性需求", 3),
    ("4 系统设计", 1),
    ("4.1 系统总体设计", 2),
    ("4.1.1 系统架构设计", 3),
    ("4.1.2 功能模块划分", 3),
    ("4.2 数据库设计", 2),
    ("4.2.1 E-R图", 3),
    ("4.2.2 数据表设计", 3),
    ("4.3 详细设计", 2),
    ("4.3.1 前端界面设计", 3),
    ("4.3.2 后端接口设计", 3),
    ("4.3.3 网关鉴权与分布式事务设计", 3),
    ("5 系统实现", 1),
    ("5.1 开发环境搭建", 2),
    ("5.2 网关与认证服务实现", 2),
    ("5.3 用户服务实现", 2),
    ("5.4 任务服务实现", 2),
    ("5.5 钱包服务实现", 2),
    ("5.6 聊天服务实现", 2),
    ("5.7 审核与客服服务实现", 2),
    ("5.8 前端三端实现", 2),
    ("6 系统测试", 1),
    ("6.1 测试环境与方法", 2),
    ("6.2 功能测试", 2),
    ("6.3 性能测试", 2),
    ("6.4 安全性测试", 2),
    ("6.5 测试结论", 2),
    ("7 总结与展望", 1),
    ("7.1 总结", 2),
    ("7.2 展望", 2),
    ("参考文献", 1),
    ("致  谢", 1),
]

REFERENCES = [
    "黄丽芬, 孙立湃. 基于三中台架构的智慧校园建设研究[J]. 扬州职业大学学报, 2026, 30(01): 58-62.",
    "何晶. 校园跑腿微信小程序的设计与开发研究[J]. 科技资讯, 2025, 23(03): 31-34.",
    "黄浩, 魏思洋, 韩彩霞. 校园快递智能无人车配送系统设计[J]. 微型电脑应用, 2024, 40(11): 27-31.",
    "李烨红, 黄培泉, 周诗婷, 等. 基于微服务架构的自动判卷系统设计与实现[J]. 现代信息科技, 2024, 8(11): 101-105+110.",
    "凌勇, 李金忠, 黄启华, 等. 基于SpringBoot和Vue框架的高校统战工作服务信息管理系统的设计与实现[J]. 广东轻工职业技术学院学报, 2024, 23(02): 16-23.",
    "Savelsbergh M, Ulmer M W. Challenges and opportunities in crowdsourced delivery planning and operations[J]. Networks, 2024, 83(1): 1-18.",
    "Jawad I, Hossain M R, Islam J, et al. Reimagining last-mile delivery: Assessing the feasibility, cost-reduction, and efficiency of a university-centric gig economy model in Bangladesh[J]. Supply Chain Insider, 2024, 14(1).",
    "Cheng R, Fessler A, Nielsen O A, et al. Assessing the potential impacts of public transport-based crowdshipping: A case study in a central district of Copenhagen[J]. Frontiers of Engineering Management, 2024, 11(4): 697-709.",
    "师亚玲. 基于众包物流的校园快递末端配送模式研究[J]. 物流工程与管理, 2023, 45(10): 88-90.",
    "何云邦, 王瑞, 张明. 校园配送机器人路径规划与系统设计[J]. 机械设计与制造工程, 2023, 52(08): 45-49.",
    "葛显龙, 刘小宁, 姜云云. 众包模式下考虑不同交付方式的配送路径优化研究[J]. 计算机工程与应用, 2025, 61(9): 370-380.",
    "秦辉东, 李娜. 智慧校园背景下高校后勤信息化服务平台构建研究[J]. 中国教育信息化, 2023(18): 72-76.",
]


def chapter_bodies():
    """返回 [(heading_fn_name, title, paragraphs_list_or_special)] 由 build 脚本解释。"""
    return None  # content assembled in build_thesis.py
