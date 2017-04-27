package com.polopoly.ps.pcmd.tool.imagemapper;

import Jama.Matrix;
import com.atex.onecms.content.InsertionInfoAspectBean;
import com.atex.onecms.content.InsertionInfoAspectUtil;
import com.atex.onecms.content.OneCMSAspectBean;
import com.atex.onecms.image.AspectRatio;
import com.atex.onecms.image.CropInfo;
import com.atex.onecms.image.ImageEditInfoAspectBean;
import com.atex.onecms.image.ImageFormat;
import com.atex.onecms.image.ImageInfoAspectBean;
import com.atex.onecms.image.Pixelation;
import com.polopoly.cm.ContentId;
import com.polopoly.cm.ExternalContentId;
import com.polopoly.cm.VersionedContentId;
import com.polopoly.cm.app.widget.impl.IllegalParameterException;
import com.polopoly.cm.client.CMException;
import com.polopoly.cm.policy.PolicyCMServer;
import com.polopoly.common.collections.Pair;
import com.polopoly.pcmd.tool.Tool;
import com.polopoly.ps.pcmd.FatalToolException;
import com.polopoly.ps.pcmd.argument.ContentIdListParameters;
import com.polopoly.ps.pcmd.tool.imagemapper.operations.Box;
import com.polopoly.ps.pcmd.tool.imagemapper.operations.Mirror;
import com.polopoly.ps.pcmd.tool.imagemapper.operations.Operation;
import com.polopoly.ps.pcmd.tool.imagemapper.operations.Rotate;
import com.polopoly.ps.pcmd.tool.imagemapper.operations.Scramble;
import com.polopoly.util.client.PolopolyContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OldImageMapperTool implements Tool<ContentIdListParameters> {

    private static final IntUnaryOperator PIXELATE_LEVEL = scrambleLevel -> 100;
    private static final Function<String, AspectRatio> ASPECT_RATIO_TRANSFORMER = s -> {
        String[] ratio = s.split(":");
        return new AspectRatio(Integer.parseInt(ratio[0]),
                               Integer.parseInt(ratio[1]));
    };

    private static final String TRANSFORMATION_SCRAMBLE = "scramble";
    private static final String TRANSFORMATION_ROTATE = "rotate";
    private static final String TRANSFORMATION_MIRROR = "mirror";
    private static final String TRANSFORMATION_CROP = "crop";

    private PolopolyContext context;

    @Override
    public void execute(PolopolyContext context, ContentIdListParameters contentIds)
            throws FatalToolException {
        Iterator<ContentId> iterator = contentIds.getContentIds();
        this.context = context;
        while (iterator.hasNext()) {
            ContentId contentId = iterator.next();
            try {
                System.out.println(contentId.getContentIdString());
                createNewImage(contentId);
            } catch (CMException |
                    IOException |
                    SAXException |
                    ParserConfigurationException |
                    IllegalParameterException e ) {
                e.printStackTrace();
            }
        }
    }


    private void createNewImage(ContentId contentId)
            throws CMException,
                   IOException,
                   IllegalParameterException,
                   SAXException,
                   ParserConfigurationException {

        PolicyCMServer cmServer = context.getPolicyCMServer();
        ImagePolicy oldImage = (ImagePolicy) cmServer.getPolicy(contentId);
        AspectedImagePolicy newImage = (AspectedImagePolicy) cmServer.createContentVersion(
                contentId.getLatestVersionId(),
                cmServer.findContentIdByExternalId(
                        //TODO externalid of AspectedImagePolicy
                        new ExternalContentId("com.package.name.AspectedImage")));

        OneCMSAspectBean oneCMSAspectBean = new OneCMSAspectBean();
        oneCMSAspectBean.setCreatedWithTemplate("act.template.Image.edit");


        uploadFile(oldImage, newImage);
        newImage.setContentData(createContentData(oldImage));
        newImage.setAspect(OneCMSAspectBean.ASPECT_NAME,
                           oneCMSAspectBean);
        newImage.setAspect(ImageInfoAspectBean.ASPECT_NAME,
                           createImageInfo(oldImage));
        newImage.setAspect(ImageEditInfoAspectBean.ASPECT_NAME,
                           createImageEditInfo(oldImage));
        newImage.setAspect(InsertionInfoAspectBean.ASPECT_NAME,
                           new InsertionInfoAspectUtil().read(oldImage));
        cmServer.commitContent(newImage);
    }

    private ImageEditInfoAspectBean createImageEditInfo(ImagePolicy oldImage)
            throws SAXException,
                   ParserConfigurationException,
                   CMException,
                   IOException,
                   IllegalParameterException {
        HashMap<String, List<String>> editModes = parseAppletState(oldImage);
        String mainFormat = "16:9";
        ImageEditInfoAspectBean aspectBean = parseEditMode(editModes.get("16:9"),
                                                           "16:9",
                                                           oldImage);
        if (!aspectBean.isFlipHorizontal() && !aspectBean.isFlipVertical() && aspectBean.getRotation() == 0) {
            aspectBean = parseEditMode(editModes.get("4:3"),
                                       "4:3",
                                       oldImage);
            mainFormat = "4:3";
        }
        for (Map.Entry<String, List<String>> entry : editModes.entrySet()) {
            if (entry.getKey().equals(mainFormat)) {
                continue;
            }
            ImageEditInfoAspectBean editModeAspectBean = parseEditMode(entry.getValue(),
                                                                       entry.getKey(),
                                                                       oldImage);
            if (editModeAspectBean.getCrops().size() > 0) {
                Map<String, CropInfo> crops = aspectBean.getCrops();
                crops.put(entry.getKey(), editModeAspectBean.getCrop(entry.getKey()));
                aspectBean.setCrops(crops);
            }
            if (editModeAspectBean.getPixelations().size() > 0) {
                List<Pixelation> pixelations = aspectBean.getPixelations();
                pixelations.addAll(editModeAspectBean.getPixelations());
                aspectBean.setPixelations(pixelations);
            }
        }
        return aspectBean;
    }

    private ImageEditInfoAspectBean parseEditMode(List<String> editMode,
                                                  String editModeName,
                                                  ImagePolicy oldImage)
            throws IllegalParameterException, CMException, IOException {
        Pair<Integer, Integer> widthHeight = getWidthHeight(oldImage);
        Box box = new Box(new Matrix(new double[][] {{0}, {0}}),
                          new Matrix(new double[][] {{0}, {widthHeight.cdr()}}),
                          new Matrix(new double[][] {{widthHeight.car()}, {widthHeight.cdr()}}),
                          new Matrix(new double[][] {{widthHeight.car()}, {0}}));

        boolean flipHorizontal = false;
        boolean flipVertical = false;
        double rotateDegrees = 0;
        boolean cropped = false;
        List<Operation> operations = new ArrayList<>();
        for (Pair<String, Map<Character, Integer>> tranformation : getTransformations(editMode)) {
            String transformationName = tranformation.car();
            Map<Character, Integer> parameters = tranformation.cdr();
            System.err.println(transformationName);
            switch (transformationName) {
                case "scramble":
                    box.addScramble(parameters.get('l'),
                                    parameters.get('r'),
                                    parameters.get('t'),
                                    parameters.get('b'),
                                    parameters.get('p'));
                    System.err.println(box);
                    break;
                case "rotate":
                    if (parameters.get('d') == 90) {
                        operations.add(Rotate.DEGREES_90);
                        box = Rotate.DEGREES_90.apply(box);
                        rotateDegrees += 90;
                    }
                    System.err.println(box);
                    break;
                case "mirror":
                    operations.add(Mirror.HORIZONTAL);
                    box = Mirror.HORIZONTAL.apply(box);
                    double degrees = rotateDegrees % 360;
                    if (degrees == 0 || degrees == 180) {
                        flipHorizontal = !flipHorizontal;
                        System.err.println("fliphorizontal:" + flipHorizontal);
                    } else {
                        flipVertical = !flipVertical;
                        System.err.println("flipvertical:" + flipVertical);
                    }
                    System.err.println(box);
                    break;
                case "crop":
                    box.crop(parameters.get('l'),
                             parameters.get('r'),
                             parameters.get('t'),
                             parameters.get('b'));
                    System.err.println(box);
                    cropped = true;
                    break;
            }
        }

        Collections.reverse(operations);
        for (Operation operation : operations) {
            box = operation.unApply(box);
        }

        ImageEditInfoAspectBean aspectBean = new ImageEditInfoAspectBean();
        if (cropped) {
            Map<String, CropInfo> crops = aspectBean.getCrops();
            crops.put(editModeName,
                      new CropInfo(box.getRectangle(),
                                   new ImageFormat(editModeName,
                                                   ASPECT_RATIO_TRANSFORMER.apply(editModeName))));
            aspectBean.setCrops(crops);
        }
        List<Pixelation> pixelations = new ArrayList<>();
        for (Box subBox : box.getScrambles()) {
            Scramble scramble = (Scramble) subBox;
            pixelations.add(new Pixelation((int) scramble.getStartPoint().get(0, 0),
                                           (int) scramble.getStartPoint().get(1, 0),
                                           (int) scramble.getWidth(),
                                           (int) scramble.getHeight(),
                                           PIXELATE_LEVEL.applyAsInt(scramble.getLevel())));
        }
        aspectBean.setFlipVertical(flipVertical);
        aspectBean.setFlipHorizontal(flipHorizontal);
        aspectBean.setRotation((int) rotateDegrees % 360);
        if (flipHorizontal) {
            System.out.println("fliphorizontal");
        }
        if (flipVertical) {
            System.out.println("flipvertical");
        }
        aspectBean.setPixelations(pixelations);
        return aspectBean;
    }

    private HashMap<String, List<String>> parseAppletState(ImagePolicy oldImage)
            throws CMException,
                   IOException,
                   ParserConfigurationException,
                   SAXException {
        String appletStateXml = oldImage.getHttpImageManager()
                                        .getSelectedImage()
                                        .getAppletStateXmlEncoded();
        InputSource inputSource = new InputSource(
                new StringReader(URLDecoder.decode(appletStateXml,
                                                   StandardCharsets.UTF_8.displayName())));
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance()
                                                                .newDocumentBuilder();
        Document document = documentBuilder.parse(inputSource);
        document.normalize();
        NodeList formats = document.getElementsByTagName("editmode");

        HashMap<String, List<String>> formatTransformations = new HashMap<>();

        for (int i = 0; i < formats.getLength(); i++) {
            Pair<String, List<String>> transformations = parseFormat((Element) formats.item(i));
            formatTransformations.put(transformations.car(), transformations.cdr());
        }
        return formatTransformations;
    }

    private Pair<String, List<String>> parseFormat(Element format) {
        format.normalize();
        Element manual = (Element) format.getElementsByTagName("manual").item(0);
        if (manual == null) {
            return null;
        }
        String label = format.getAttribute("label");
        List<String> transformationList = new ArrayList<>();
        NodeList transformations = manual.getElementsByTagName("transform");
        for (int i = 0; i < transformations.getLength(); i++) {
            transformationList.add(transformations.item(i)
                                                  .getAttributes()
                                                  .getNamedItem("code")
                                                  .getNodeValue());
        }
        return new Pair<>(label, transformationList);
    }

    private List<Pair<String, Map<Character, Integer>>> getTransformations(List<String> rawTransformations)
            throws IllegalParameterException {
        List<Pair<String, Map<Character, Integer>>> transformations = new ArrayList<>();
        for (String rawTransformation : rawTransformations) {
            if (rawTransformation.contains(TRANSFORMATION_CROP)) {
                String rawParameters =
                        rawTransformation.substring(TRANSFORMATION_CROP.length());
                transformations.add(new Pair<>(TRANSFORMATION_CROP,
                                               parseParameters(rawParameters)));
            } else if (rawTransformation.contains(TRANSFORMATION_SCRAMBLE)) {
                String rawParameters =
                        rawTransformation.substring(TRANSFORMATION_SCRAMBLE.length());
                transformations.add(new Pair<>(TRANSFORMATION_SCRAMBLE,
                                               parseParameters(rawParameters)));
            } else if (rawTransformation.contains(TRANSFORMATION_MIRROR)) {
                transformations.add(new Pair<>(TRANSFORMATION_MIRROR, null));
            } else if (rawTransformation.contains(TRANSFORMATION_ROTATE)) {
                String rawParameters =
                        rawTransformation.substring(TRANSFORMATION_ROTATE.length());
                HashMap<Character, Integer> parameters = new HashMap<>();
                parameters.put('d', Integer.parseInt(rawParameters));
                transformations.add(new Pair<>(TRANSFORMATION_ROTATE, parameters));
            }
        }
        return transformations;
    }

    private Map<Character, Integer> parseParameters(String code) throws IllegalParameterException {
        Matcher matcher = Pattern.compile("[^0-9]").matcher(code);

        if (!matcher.find()) {
            throw new IllegalParameterException("no parameters found in code");
        }

        Map<Character, Integer> parameters = new HashMap<>();
        int start = matcher.start();
        while (matcher.find()) {
            parameters.put(code.charAt(start),
                           Integer.parseInt(code.substring(start + 1, matcher.start())));
            start = matcher.start();

        }
        parameters.put(code.charAt(start),
                       Integer.parseInt(code.substring(start + 1, code.length())));

        return parameters;
    }

    private void uploadFile(ImagePolicy oldImage, AspectedImagePolicy newImage)
            throws CMException, IOException {
        URL url = oldImage.getHttpImageManager().getUrl();
        String filePath = url.getFile();
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        newImage.importHttpImage(fileName, url.openConnection().getInputStream());
    }

    private ImageInfoAspectBean createImageInfo(ImagePolicy oldImage)
            throws IOException, CMException {
        Pair<Integer, Integer> widthHeight = getWidthHeight(oldImage);
        String filePath = oldImage.getHttpImageManager()
                                  .getUrl()
                                  .getFile();
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);

        return new ImageInfoAspectBean(fileName, widthHeight.car(), widthHeight.cdr());
    }

    private Pair<Integer, Integer> getWidthHeight(ImagePolicy oldImage)
            throws CMException, IOException {
        //TODO ugly to download whole image twice
        BufferedImage image = ImageIO.read(oldImage.getHttpImageManager().getUrl());

        int height = image.getHeight();
        int width = image.getWidth();

        return new Pair<>(width, height);
    }

    private ImageContentDataBean createContentData(ImagePolicy oldImage)
            throws CMException {
        ImageContentDataBean contentData = new ImageContentDataBean();

        String name = oldImage.getName();
        if (name != null) {
            contentData.setTitle(name);
        }
        String subline = oldImage.getSubline();
        if (subline != null) {
            contentData.setSubline(subline);
        }
        String photoCredit = oldImage.getPhotoCredit();
        if (photoCredit != null) {
            contentData.setPhotoCredit(photoCredit);
        }
        Date onTime = oldImage.getOnTime();
        if (onTime != null) {
            contentData.setOnTime(onTime.getTime());
        }
        Date offTime = oldImage.getOffTime();
        if (offTime != null) {
            contentData.setOffTime(offTime.getTime());
        }

        return contentData;
    }

    @Override
    public ContentIdListParameters createParameters() {
        return new ContentIdListParameters();
    }

    @Override
    public String getHelp() {
        return "Takes old Applet Images and remakes them into OneCMS Images.";
    }
}
