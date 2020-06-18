import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Formik } from "formik";
import * as Yup from "yup";
import { Page, Form } from "./ChangePasswordForm";
import { useState } from "react";
import zxcvbn from "zxcvbn";

const TestHarness: React.FunctionComponent = () => {
  const [strength, setStrength] = useState(0);
  let currentStrength = strength;

  const minStrength = 3;
  const thresholdLength = 7;

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required")
    .min(thresholdLength, "Password is short")
    .test(
      "password-strength",
      "Password is weak",
      () => currentStrength > minStrength,
    );

  const confirmPasswordSchema = Yup.string()
    .label("Confirm Password")
    .required("Required")
    .test("password-match", "Passwords must match", function (value) {
      const { resolve } = this;
      const ref = Yup.ref("password");
      return value === resolve(ref);
    });

  const validationSchema = Yup.object().shape({
    password: passwordSchema,
    confirmPassword: confirmPasswordSchema,
  });

  return (
    <Page>
      <Formik
        initialValues={{ userId: "", password: "", confirmPassword: "" }}
        validationSchema={validationSchema}
        onSubmit={(values, actions) => {
          setTimeout(() => {
            alert(JSON.stringify(values, null, 2));
            actions.setSubmitting(false);
          }, 1000);
        }}
      >
        {(props) => {
          const handler = (e: React.ChangeEvent<HTMLInputElement>) => {
            if (e.target.id === "password") {
              const score = zxcvbn(e.target.value).score;
              setStrength(score);
              currentStrength = score;
            }
            props.handleChange(e);
          };

          return (
            <Form
              {...props}
              strength={strength}
              minStrength={minStrength}
              thresholdLength={thresholdLength}
              handleChange={handler}
            />
          );
        }}
      </Formik>
    </Page>
  );
};

storiesOf("Authentication", module).add("Change Password Form", () => (
  <TestHarness />
));