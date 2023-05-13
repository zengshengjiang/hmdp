//package com.hmdp.utils;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.mail.SimpleMailMessage;
//import org.springframework.mail.javamail.JavaMailSender;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.Resource;
//
//@Service
//public class SendMailUtils {
//
//    @Autowired
//    private JavaMailSender javaMailSender;
//
//    //发送人
//    //@Value("${spring.mail.username}")
//    private String from = "2982419675@qq.com";
//    //接收人
//    //@Value("${spring.mail.username}")
//    private String to = "2982419675@qq.com";
//    //标题
//    //private String subject = "验证码";
//    //正文
//    //private String context = "您的验证码为:";
//
//    public void sendMail(String phone, String code) {
//        String context = "验证码" + code + ",请妥善保存";
//        String subject = "手机号:" + phone + "验证码";
//
//        SimpleMailMessage message = new SimpleMailMessage();
//        //在后面（***） 可替换发件人姓名
//        message.setFrom(from + "(瑞吉外卖项目)");
//        message.setTo(to);
//        message.setSubject(subject);
//        message.setText(context);
//
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                javaMailSender.send(message);
//            }
//        }).start();
//        context = "";
//        subject = "";
//
//    }
//}
